from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base
from .settings import settings

# БАЗОВЫЙ КЛАСС ДЛЯ ORM-МОДЕЛЕЙ
Base = declarative_base()

DB_URL = (
    f"postgresql://{settings.db_user}:{settings.db_password}"
    f"@{settings.db_host}:{settings.db_port}/{settings.db_name}"
)

engine = create_engine(
    DB_URL,
    pool_pre_ping=True,
    pool_size=20,
    max_overflow=20,
    pool_recycle=3600,
    connect_args={"connect_timeout": 10},
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# dependency для FastAPI
from fastapi import Depends
from . import models

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
