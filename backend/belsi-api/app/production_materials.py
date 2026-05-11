"""
Materials — каталог, остатки, заявки.

Роли:
- ProductionChief: видит каталог + остатки + утверждает заявки
- Supplier (снабженец): создаёт заявки, видит свой queue
- SeniorWorker / Worker: видит остатки своей фабрики (read-only)
- Admin / Curator: всё
"""
from __future__ import annotations
from datetime import datetime, timezone
from typing import Optional, List
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict
from sqlalchemy.orm import Session
from sqlalchemy import text

from .db import get_db
from .auth import get_current_user
from .models import User

router = APIRouter(prefix="/production/materials", tags=["production-materials"])


# ============================================================
# Schemas
# ============================================================

class MaterialOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    code: str
    name: str
    unit: str
    category: Optional[str] = None
    min_stock: int = 0
    active: bool = True


class InventoryItemOut(BaseModel):
    facility_id: UUID
    material_id: UUID
    code: str
    name: str
    unit: str
    category: Optional[str] = None
    quantity: int
    min_stock: int
    is_low: bool  # quantity < min_stock
    updated_at: datetime


class MaterialOrderOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    facility_id: UUID
    facility_name: Optional[str] = None
    material_id: UUID
    material_code: str
    material_name: str
    material_unit: str
    quantity_requested: int
    quantity_delivered: int = 0
    status: str
    requested_by: UUID
    requested_by_name: Optional[str] = None
    approved_by: Optional[UUID] = None
    supplier_id: Optional[UUID] = None
    supplier_name: Optional[str] = None
    note: Optional[str] = None
    created_at: datetime
    updated_at: datetime


class MaterialOrderCreate(BaseModel):
    facility_id: UUID
    material_id: UUID
    quantity_requested: int
    note: Optional[str] = None


class MaterialOrderStatusUpdate(BaseModel):
    status: str  # approved / ordered / delivered / cancelled
    quantity_delivered: Optional[int] = None
    note: Optional[str] = None


class InventoryAdjust(BaseModel):
    facility_id: UUID
    material_id: UUID
    delta: int  # положительный — приход, отрицательный — расход
    reason: Optional[str] = None


# ============================================================
# Каталог
# ============================================================

@router.get("/catalog", response_model=List[MaterialOut])
def list_catalog(
    active_only: bool = Query(True),
    category: Optional[str] = Query(None),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Каталог материалов."""
    where = []
    params = {}
    if active_only:
        where.append("active = TRUE")
    if category:
        where.append("category = :cat")
        params["cat"] = category
    where_sql = ("WHERE " + " AND ".join(where)) if where else ""

    rows = db.execute(
        text(f"SELECT * FROM materials_catalog {where_sql} ORDER BY category, name"),
        params,
    ).mappings().all()
    return [MaterialOut(**dict(r)) for r in rows]


# ============================================================
# Остатки на фабрике
# ============================================================

@router.get("/inventory", response_model=List[InventoryItemOut])
def list_inventory(
    facility_id: UUID = Query(...),
    only_low: bool = Query(False, description="Только материалы ниже мин. остатка"),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Остатки материалов на фабрике."""
    rows = db.execute(
        text("""
            SELECT
                COALESCE(i.facility_id, :fid) AS facility_id,
                c.id AS material_id,
                c.code, c.name, c.unit, c.category, c.min_stock,
                COALESCE(i.quantity, 0) AS quantity,
                COALESCE(i.updated_at, NOW()) AS updated_at
            FROM materials_catalog c
            LEFT JOIN materials_inventory i
                   ON i.material_id = c.id AND i.facility_id = :fid
            WHERE c.active = TRUE
            ORDER BY c.category, c.name
        """),
        {"fid": str(facility_id)},
    ).mappings().all()

    items = []
    for r in rows:
        is_low = (r["quantity"] or 0) < (r["min_stock"] or 0)
        if only_low and not is_low:
            continue
        items.append(InventoryItemOut(
            facility_id=r["facility_id"],
            material_id=r["material_id"],
            code=r["code"],
            name=r["name"],
            unit=r["unit"],
            category=r["category"],
            quantity=r["quantity"] or 0,
            min_stock=r["min_stock"] or 0,
            is_low=is_low,
            updated_at=r["updated_at"],
        ))
    return items


@router.post("/inventory/adjust", status_code=200)
def adjust_inventory(
    payload: InventoryAdjust,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Изменить остаток. Доступно для Chief/Supplier/Curator."""
    if current_user.role not in ("production_chief", "supplier", "curator", "coordinator"):
        raise HTTPException(status_code=403, detail="Недостаточно прав")

    db.execute(
        text("""
            INSERT INTO materials_inventory (facility_id, material_id, quantity)
            VALUES (:fid, :mid, GREATEST(0, :delta))
            ON CONFLICT (facility_id, material_id)
            DO UPDATE SET
                quantity = GREATEST(0, materials_inventory.quantity + :delta),
                updated_at = NOW()
        """),
        {"fid": str(payload.facility_id), "mid": str(payload.material_id), "delta": payload.delta},
    )
    db.commit()
    return {"ok": True}


# ============================================================
# Заявки на материалы
# ============================================================

@router.get("/orders", response_model=List[MaterialOrderOut])
def list_orders(
    facility_id: Optional[UUID] = Query(None),
    status: Optional[str] = Query(None),
    mine: bool = Query(False, description="Только мои (для снабженца)"),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Список заявок."""
    where = []
    params = {}
    if facility_id:
        where.append("o.facility_id = :fid")
        params["fid"] = str(facility_id)
    if status:
        where.append("o.status = :status")
        params["status"] = status
    if mine and current_user.role == "supplier":
        where.append("(o.supplier_id = :uid OR o.supplier_id IS NULL)")
        params["uid"] = str(current_user.id)
    elif mine:
        where.append("o.requested_by = :uid")
        params["uid"] = str(current_user.id)

    where_sql = ("WHERE " + " AND ".join(where)) if where else ""

    rows = db.execute(
        text(f"""
            SELECT o.*,
                   c.code AS material_code, c.name AS material_name, c.unit AS material_unit,
                   so.name AS facility_name,
                   (ru.first_name || ' ' || COALESCE(ru.last_name, '')) AS requested_by_name,
                   (su.first_name || ' ' || COALESCE(su.last_name, '')) AS supplier_name
            FROM material_orders o
            JOIN materials_catalog c ON c.id = o.material_id
            JOIN site_objects so ON so.id = o.facility_id
            JOIN users ru ON ru.id = o.requested_by
            LEFT JOIN users su ON su.id = o.supplier_id
            {where_sql}
            ORDER BY o.created_at DESC
            LIMIT 200
        """),
        params,
    ).mappings().all()
    return [MaterialOrderOut(**dict(r)) for r in rows]


@router.post("/orders", response_model=MaterialOrderOut, status_code=201)
def create_order(
    payload: MaterialOrderCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Создать заявку на материал."""
    if current_user.role not in ("supplier", "production_chief", "senior_worker", "curator", "coordinator"):
        raise HTTPException(status_code=403, detail="Недостаточно прав на создание заявки")

    row = db.execute(
        text("""
            INSERT INTO material_orders (facility_id, material_id, quantity_requested, requested_by, note, status, supplier_id)
            VALUES (:fid, :mid, :qty, :uid, :note, 'pending', :supplier)
            RETURNING *
        """),
        {
            "fid": str(payload.facility_id),
            "mid": str(payload.material_id),
            "qty": payload.quantity_requested,
            "uid": str(current_user.id),
            "note": payload.note,
            "supplier": str(current_user.id) if current_user.role == "supplier" else None,
        },
    ).mappings().first()
    db.commit()

    # Возвращаем с расширенной инфой
    full = db.execute(
        text("""
            SELECT o.*,
                   c.code AS material_code, c.name AS material_name, c.unit AS material_unit,
                   so.name AS facility_name,
                   (ru.first_name || ' ' || COALESCE(ru.last_name, '')) AS requested_by_name,
                   NULL::text AS supplier_name
            FROM material_orders o
            JOIN materials_catalog c ON c.id = o.material_id
            JOIN site_objects so ON so.id = o.facility_id
            JOIN users ru ON ru.id = o.requested_by
            WHERE o.id = :oid
        """),
        {"oid": str(row["id"])},
    ).mappings().first()
    return MaterialOrderOut(**dict(full))


@router.patch("/orders/{order_id}", response_model=MaterialOrderOut)
def update_order_status(
    order_id: UUID,
    payload: MaterialOrderStatusUpdate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Сменить статус заявки. Approve — Chief/Curator. Deliver — Supplier."""
    valid_statuses = {"approved", "ordered", "delivered", "cancelled"}
    if payload.status not in valid_statuses:
        raise HTTPException(status_code=400, detail=f"Статус должен быть одним из: {valid_statuses}")

    if payload.status in ("approved", "cancelled") and current_user.role not in ("production_chief", "curator", "coordinator"):
        raise HTTPException(status_code=403, detail="Утверждение/отмена — только Chief")

    if payload.status in ("ordered", "delivered") and current_user.role not in ("supplier", "curator", "coordinator"):
        raise HTTPException(status_code=403, detail="Заказ/доставка — только Supplier")

    update_fields = ["status = :status", "updated_at = NOW()"]
    params = {"oid": str(order_id), "status": payload.status}

    if payload.status == "approved":
        update_fields.append("approved_by = :uid")
        params["uid"] = str(current_user.id)
    elif payload.status == "delivered" and payload.quantity_delivered is not None:
        update_fields.append("quantity_delivered = :qty")
        params["qty"] = payload.quantity_delivered

    if payload.note:
        update_fields.append("note = :note")
        params["note"] = payload.note

    row = db.execute(
        text(f"UPDATE material_orders SET {', '.join(update_fields)} WHERE id = :oid RETURNING id"),
        params,
    ).mappings().first()
    if not row:
        raise HTTPException(status_code=404, detail="Заявка не найдена")

    # При доставке — увеличиваем остаток
    if payload.status == "delivered" and payload.quantity_delivered:
        order = db.execute(
            text("SELECT facility_id, material_id FROM material_orders WHERE id = :oid"),
            {"oid": str(order_id)},
        ).mappings().first()
        if order:
            db.execute(
                text("""
                    INSERT INTO materials_inventory (facility_id, material_id, quantity)
                    VALUES (:fid, :mid, :qty)
                    ON CONFLICT (facility_id, material_id)
                    DO UPDATE SET
                        quantity = materials_inventory.quantity + :qty,
                        updated_at = NOW()
                """),
                {"fid": str(order["facility_id"]), "mid": str(order["material_id"]), "qty": payload.quantity_delivered},
            )

    db.commit()

    # Re-fetch full order
    full = db.execute(
        text("""
            SELECT o.*,
                   c.code AS material_code, c.name AS material_name, c.unit AS material_unit,
                   so.name AS facility_name,
                   (ru.first_name || ' ' || COALESCE(ru.last_name, '')) AS requested_by_name,
                   (su.first_name || ' ' || COALESCE(su.last_name, '')) AS supplier_name
            FROM material_orders o
            JOIN materials_catalog c ON c.id = o.material_id
            JOIN site_objects so ON so.id = o.facility_id
            JOIN users ru ON ru.id = o.requested_by
            LEFT JOIN users su ON su.id = o.supplier_id
            WHERE o.id = :oid
        """),
        {"oid": str(order_id)},
    ).mappings().first()
    return MaterialOrderOut(**dict(full))
