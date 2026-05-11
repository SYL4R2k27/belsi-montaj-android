"""
Pydantic схемы для модуля инструментов
"""
from pydantic import BaseModel, Field, ConfigDict
from uuid import UUID
from typing import Optional, List
from datetime import datetime


# ============= User Info Schemas =============

class UserBrief(BaseModel):
    """Краткая информация о пользователе"""
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    phone: str
    role: str
    first_name: Optional[str] = None
    last_name: Optional[str] = None
    full_name: str


# ============= Tool Schemas =============

class ToolCreate(BaseModel):
    """Создание нового инструмента"""
    name: str = Field(..., min_length=1, max_length=200, description="Название инструмента")
    description: Optional[str] = Field(None, max_length=1000, description="Описание")
    serial_number: Optional[str] = Field(None, max_length=100, description="Серийный номер")
    photo_url: Optional[str] = Field(None, max_length=500, description="URL фото")


class ToolOut(BaseModel):
    """Инструмент (ответ)"""
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    name: str
    description: Optional[str]
    serial_number: Optional[str]
    photo_url: Optional[str]
    foreman_id: UUID
    foreman: Optional[UserBrief] = None  # Информация о бригадире
    status: str  # available, issued, lost, repair
    created_at: datetime
    updated_at: datetime


class ToolsListResponse(BaseModel):
    """Список инструментов (обернутый)"""
    items: List[ToolOut]


# ============= Transaction Schemas =============

class ToolIssueRequest(BaseModel):
    """Запрос на выдачу инструмента"""
    tool_id: UUID = Field(..., description="ID инструмента")
    installer_id: UUID = Field(..., description="ID монтажника")
    comment: Optional[str] = Field(None, max_length=500, description="Комментарий")
    photo_url: Optional[str] = Field(None, max_length=500, description="URL фото при выдаче")


class ToolReturnRequest(BaseModel):
    """Запрос на возврат инструмента"""
    condition: str = Field(..., description="Состояние: good, damaged, broken")
    comment: Optional[str] = Field(None, max_length=500, description="Комментарий")
    photo_url: Optional[str] = Field(None, max_length=500, description="URL фото при возврате")

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "condition": "good",
                "comment": "Все в порядке",
                "photo_url": "https://..."
            }
        }
    )


class ToolTransactionOut(BaseModel):
    """Транзакция (ответ)"""
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    tool_id: UUID
    tool: Optional[ToolOut] = None  # Информация об инструменте

    installer_id: UUID
    installer: Optional[UserBrief] = None  # Информация о монтажнике

    # Выдача
    issued_by: UUID
    issued_by_user: Optional[UserBrief] = None  # Кто выдал
    issued_at: datetime
    issue_comment: Optional[str]
    issue_photo_url: Optional[str]

    # Возврат
    returned_at: Optional[datetime]
    returned_to: Optional[UUID]
    returned_to_user: Optional[UserBrief] = None  # Кто принял
    return_condition: Optional[str]
    return_comment: Optional[str]
    return_photo_url: Optional[str]

    status: str  # issued, returned
    created_at: datetime


class ToolTransactionsListResponse(BaseModel):
    """Список транзакций (обернутый)"""
    items: List[ToolTransactionOut]


# ============= Photo Upload =============

class ToolPhotoUploadResponse(BaseModel):
    """Ответ после загрузки фото"""
    photoUrl: str = Field(..., description="URL загруженного фото")
