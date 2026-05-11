from __future__ import annotations

from logging.config import fileConfig

from alembic import context
from sqlalchemy import engine_from_config, pool

from app.settings import settings
from app.db import Base  # важно: в db.py должен быть Base = declarative_base()

# ---------------------------------------------------------
# Alembic Config объект, предоставляет доступ к .ini файлу
# ---------------------------------------------------------

config = context.config

# если есть alembic.ini — подгружаем логирование
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

# ГЛАВНОЕ: выставляем строку подключения из settings.database_url
config.set_main_option("sqlalchemy.url", settings.database_url)

# target_metadata — метаданные всех моделей
target_metadata = Base.metadata


def run_migrations_offline() -> None:
    """Запуск миграций в offline-режиме (генерирует SQL без коннекта)."""
    url = config.get_main_option("sqlalchemy.url")
    context.configure(
        url=url,
        target_metadata=target_metadata,
        literal_binds=True,
        compare_type=True,
    )

    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    """Запуск миграций в online-режиме (подключаемся к БД и выполняем)."""
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )

    with connectable.connect() as connection:
        context.configure(
            connection=connection,
            target_metadata=target_metadata,
            compare_type=True,
        )

        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
