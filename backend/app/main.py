"""Local-only demonstration API for the native KBR CargoShare clients.

IMPORTANT: In production mode, booking/auth/admin mutations are disabled until
SMS verification, identity checks, payments, and robust concurrency are added.
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import math
import os
import secrets
import time
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Query, status
from fastapi.responses import FileResponse
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator
from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, UniqueConstraint, create_engine, select
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column
from sqlalchemy.pool import StaticPool


@dataclass
class Settings:
    demo_mode: bool = os.getenv('CARGOSHARE_DEMO_MODE', 'false').lower() == 'true'
    admin_key: str = os.getenv('CARGOSHARE_ADMIN_KEY', '')
    session_secret: str = os.getenv('CARGOSHARE_SESSION_SECRET', '') or secrets.token_urlsafe(32)
    database_url: str = os.getenv('CARGOSHARE_DATABASE_URL', 'sqlite:///./demo.db')


settings = Settings()
kwargs = {'connect_args': {'check_same_thread': False}}
if settings.database_url in ('sqlite://', 'sqlite:///:memory:'):
    kwargs['poolclass'] = StaticPool
engine = create_engine(settings.database_url, **kwargs)


class Base(DeclarativeBase):
    pass


class Vehicle(Base):
    __tablename__ = 'vehicles'
    id: Mapped[str] = mapped_column(String, primary_key=True)
    name: Mapped[str] = mapped_column(String)
    type: Mapped[str] = mapped_column(String)
    payload_kg: Mapped[int] = mapped_column(Integer)
    cargo_volume_m3: Mapped[int] = mapped_column(Integer)
    hourly_rate_rub: Mapped[int] = mapped_column(Integer)
    daily_rate_rub: Mapped[int] = mapped_column(Integer)
    transmission: Mapped[str] = mapped_column(String)
    pickup_location: Mapped[str] = mapped_column(String)
    description: Mapped[str] = mapped_column(String, default='', server_default='', nullable=False)
    image_url: Mapped[str] = mapped_column(String, default='', server_default='', nullable=False)
    is_published: Mapped[bool] = mapped_column(Boolean, default=True, server_default='1', nullable=False)
    # Coordinates are operator-supplied, never inferred from a street-name string.
    latitude: Mapped[float | None] = mapped_column(Float, nullable=True)
    longitude: Mapped[float | None] = mapped_column(Float, nullable=True)


class Booking(Base):
    __tablename__ = 'bookings'
    __table_args__ = (UniqueConstraint('user_id', 'idempotency_key', name='unique_user_intent'),)
    id: Mapped[str] = mapped_column(String, primary_key=True)
    user_id: Mapped[str] = mapped_column(String)
    vehicle_id: Mapped[str] = mapped_column(ForeignKey('vehicles.id'))
    start_at: Mapped[datetime] = mapped_column(DateTime)
    end_at: Mapped[datetime] = mapped_column(DateTime)
    total_rub: Mapped[int] = mapped_column(Integer)
    status: Mapped[str] = mapped_column(String)
    created_at: Mapped[datetime] = mapped_column(DateTime)
    expires_at: Mapped[datetime] = mapped_column(DateTime)
    idempotency_key: Mapped[str | None] = mapped_column(String, nullable=True)
    idempotency_hash: Mapped[str | None] = mapped_column(String, nullable=True)


class BookingEvent(Base):
    """Append-only demo timeline; no personal identifiers or fake vehicle telemetry."""
    __tablename__ = 'booking_events'
    id: Mapped[str] = mapped_column(String, primary_key=True)
    booking_id: Mapped[str] = mapped_column(ForeignKey('bookings.id'), index=True)
    event_type: Mapped[str] = mapped_column(String(30))
    occurred_at: Mapped[datetime] = mapped_column(DateTime)


def append_booking_event(session: Session, booking_id: str, event_type: str) -> None:
    session.add(BookingEvent(id=str(uuid.uuid4()), booking_id=booking_id,
                             event_type=event_type,
                             occurred_at=datetime.now(timezone.utc).replace(tzinfo=None)))


def expire_pending_bookings(session: Session) -> int:
    """Synchronize demo reservation expiry exactly once per booking.

    Only unconfirmed reservations expire. This makes the visible status agree
    with capacity checks; no real payment or hardware operation occurs.
    """
    now = datetime.now(timezone.utc).replace(tzinfo=None)
    expired = session.scalars(select(Booking).where(
        Booking.status == 'PENDING', Booking.expires_at <= now,
    )).all()
    for booking in expired:
        booking.status = 'EXPIRED'
        append_booking_event(session, booking.id, 'EXPIRED')
    return len(expired)


def transition_demo_booking(booking_id: str, *, expected: str, target: str, event: str) -> dict:
    """Admin-only handlers call this after auth. No actual vehicle handover occurs."""
    with Session(engine) as session:
        if expire_pending_bookings(session):
            session.commit()
        booking = session.get(Booking, booking_id)
        if booking is None:
            raise HTTPException(404, detail='Booking not found')
        if booking.status != expected:
            raise HTTPException(409, detail=f'Expected booking status {expected}')
        booking.status = target
        append_booking_event(session, booking_id, event)
        session.commit()
        return booking_out(booking)


DEMO_VEHICLES = [
    dict(id='gazelle-next', name='ГАЗель NEXT', type='Тентованный фургон', payload_kg=1500,
         cargo_volume_m3=16, hourly_rate_rub=550, daily_rate_rub=3400,
         transmission='Механика', pickup_location='Нальчик · демонстрационная точка'),
    dict(id='ford-transit', name='Ford Transit', type='Цельнометаллический фургон', payload_kg=1200,
         cargo_volume_m3=12, hourly_rate_rub=650, daily_rate_rub=3900,
         transmission='Механика', pickup_location='Нальчик · демонстрационная точка'),
    dict(id='largus-van', name='LADA Largus фургон', type='Компактный фургон', payload_kg=650,
         cargo_volume_m3=3, hourly_rate_rub=350, daily_rate_rub=2200,
         transmission='Механика', pickup_location='Нальчик · демонстрационная точка'),
    dict(id='gazelle-next-2', name='ГАЗель NEXT №2', type='Тентованный фургон', payload_kg=1500,
         cargo_volume_m3=16, hourly_rate_rub=550, daily_rate_rub=3400,
         transmission='Механика', pickup_location='Нальчик · учебная точка №4 (запад)',
         latitude=43.4824, longitude=43.5856),
]


def seed():
    Base.metadata.create_all(engine)
    # Development-only additive SQLite migration to retain existing demo fleet edits.
    # Real deployments must use versioned migrations (e.g. Alembic).
    if settings.demo_mode and engine.dialect.name == 'sqlite':
        with engine.begin() as connection:
            existing = {row[1] for row in connection.exec_driver_sql('PRAGMA table_info(vehicles)')}
            for column, definition in (
                ('description', "TEXT NOT NULL DEFAULT ''"),
                ('image_url', "TEXT NOT NULL DEFAULT ''"),
                ('is_published', 'BOOLEAN NOT NULL DEFAULT 1'),
                ('latitude', 'REAL'),
                ('longitude', 'REAL'),
            ):
                if column not in existing:
                    connection.exec_driver_sql(f'ALTER TABLE vehicles ADD COLUMN {column} {definition}')
    if settings.demo_mode:
        with Session(engine) as session:
            for car in DEMO_VEHICLES:
                if session.get(Vehicle, car['id']) is None:
                    session.add(Vehicle(**car))
            session.commit()


def reset_demo_db():
    """Test helper: never use for production or real customer data."""
    if not settings.demo_mode:
        raise RuntimeError('Cannot reset live data')
    Base.metadata.drop_all(engine)
    seed()


seed()
app = FastAPI(title='KBR CargoShare Demo API', version='2.2.0-alpha',
              description='Sample fleet and bookings only. Not production-ready.')


class BookingInput(BaseModel):
    model_config = ConfigDict(extra='forbid')
    vehicleId: str
    startAt: datetime
    endAt: datetime

    @field_validator('startAt', 'endAt')
    @classmethod
    def offset_required(cls, value: datetime):
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError('Timezone offset required')
        return value

    @model_validator(mode='after')
    def valid_window(self):
        now = datetime.now(timezone.utc)
        if self.startAt < now - timedelta(minutes=1):
            raise ValueError('Start must be in the future')
        duration = self.endAt - self.startAt
        if duration < timedelta(hours=2) or duration > timedelta(days=30):
            raise ValueError('Rental must last from 2 hours to 30 days')
        if self.startAt > now + timedelta(days=90):
            raise ValueError('Bookings limited to 90 days ahead')
        return self


class FleetBase(BaseModel):
    model_config = ConfigDict(extra='forbid')
    name: str = Field(min_length=1, max_length=100)
    type: str = Field(min_length=1, max_length=120)
    payloadKg: int = Field(ge=1, le=50000)
    cargoVolumeM3: int = Field(ge=0, le=500)
    hourlyPriceRub: int = Field(ge=1, le=1000000)
    dailyPriceRub: int = Field(ge=1, le=10000000)
    transmission: str = Field(min_length=1, max_length=40)
    pickupLocation: str = Field(min_length=1, max_length=180)
    description: str = Field(default='', max_length=1500)
    imageUrl: str = Field(default='', max_length=1000)
    isPublished: bool = True
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)

    @model_validator(mode='after')
    def coordinates_both_or_neither(self):
        if (self.latitude is None) != (self.longitude is None):
            raise ValueError('Latitude and longitude must be set together')
        return self

    @field_validator('name', 'type', 'transmission', 'pickupLocation')
    @classmethod
    def no_blank_value(cls, value: str):
        if not value.strip():
            raise ValueError('Field must not be blank')
        return value.strip()

    @field_validator('imageUrl')
    @classmethod
    def safe_image_url(cls, value: str):
        if value and not value.startswith('https://'):
            raise ValueError('Only HTTPS image URLs are supported')
        return value


class FleetPatch(BaseModel):
    model_config = ConfigDict(extra='forbid')
    name: str | None = Field(default=None, min_length=1, max_length=100)
    type: str | None = Field(default=None, min_length=1, max_length=120)
    payloadKg: int | None = Field(default=None, ge=1, le=50000)
    cargoVolumeM3: int | None = Field(default=None, ge=0, le=500)
    hourlyPriceRub: int | None = Field(default=None, ge=1, le=1000000)
    dailyPriceRub: int | None = Field(default=None, ge=1, le=10000000)
    transmission: str | None = Field(default=None, min_length=1, max_length=40)
    pickupLocation: str | None = Field(default=None, min_length=1, max_length=180)
    description: str | None = Field(default=None, max_length=1500)
    imageUrl: str | None = Field(default=None, max_length=1000)
    isPublished: bool | None = None
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)

    @field_validator('name', 'type', 'transmission', 'pickupLocation')
    @classmethod
    def no_blank_value(cls, value: str | None):
        if value is not None and not value.strip():
            raise ValueError('Field must not be blank')
        return value.strip() if value is not None else None

    @field_validator('imageUrl')
    @classmethod
    def safe_image_url(cls, value: str | None):
        if value and not value.startswith('https://'):
            raise ValueError('Only HTTPS image URLs are supported')
        return value

    @model_validator(mode='after')
    def requires_changes(self):
        changes = self.model_dump(exclude_unset=True)
        if not changes or any(value is None for key, value in changes.items() if key not in ('latitude', 'longitude')):
            raise ValueError('At least one valid field must be provided')
        if ('latitude' in changes) != ('longitude' in changes):
            raise ValueError('Latitude and longitude must be updated together')
        if ('latitude' in changes) and ((changes['latitude'] is None) != (changes['longitude'] is None)):
            raise ValueError('Coordinates must be both numbers or both null')
        return self


FIELD_MAP = {
    'name': 'name', 'type': 'type', 'payloadKg': 'payload_kg',
    'cargoVolumeM3': 'cargo_volume_m3', 'hourlyPriceRub': 'hourly_rate_rub',
    'dailyPriceRub': 'daily_rate_rub', 'transmission': 'transmission',
    'pickupLocation': 'pickup_location', 'description': 'description',
    'imageUrl': 'image_url', 'isPublished': 'is_published',
    'latitude': 'latitude', 'longitude': 'longitude',
}


def apply_vehicle_fields(v: Vehicle, values: dict) -> None:
    for field, value in values.items():
        setattr(v, FIELD_MAP[field], value)


def vehicle_out(v: Vehicle) -> dict:
    return {'id': v.id, 'name': v.name, 'type': v.type,
            'payloadKg': v.payload_kg, 'cargoVolumeM3': v.cargo_volume_m3,
            'hourlyPriceRub': v.hourly_rate_rub, 'dailyPriceRub': v.daily_rate_rub,
            'transmission': v.transmission, 'pickupLocation': v.pickup_location,
            'description': v.description, 'imageUrl': v.image_url, 'isPublished': v.is_published,
            'latitude': v.latitude, 'longitude': v.longitude,
            'isDemo': settings.demo_mode}


def booking_out(b: Booking) -> dict:
    return {'id': b.id, 'vehicleId': b.vehicle_id, 'startAt': b.start_at.replace(tzinfo=timezone.utc).isoformat(),
            'endAt': b.end_at.replace(tzinfo=timezone.utc).isoformat(), 'totalRub': b.total_rub,
            'status': b.status, 'createdAt': b.created_at.replace(tzinfo=timezone.utc).isoformat()}


def utc_naive(dt: datetime) -> datetime:
    return dt.astimezone(timezone.utc).replace(tzinfo=None)


def calculate_price(v: Vehicle, start: datetime, end: datetime) -> int:
    hours = math.ceil((end - start).total_seconds() / 3600)
    days, rest = divmod(hours, 24)
    return days * v.daily_rate_rub + min(rest * v.hourly_rate_rub, v.daily_rate_rub)


def demo_token() -> str:
    expires = int(time.time()) + 86400
    message = f'demo-user:{expires}'
    sig = hmac.new(settings.session_secret.encode(), message.encode(), hashlib.sha256).hexdigest()
    return base64.urlsafe_b64encode(f'{message}:{sig}'.encode()).decode()


def demo_auth(authorization: Annotated[str | None, Header()] = None) -> str:
    if not settings.demo_mode:
        raise HTTPException(503, detail='Production sign-in is not configured')
    if not authorization or not authorization.startswith('Bearer '):
        raise HTTPException(401, detail='Demo session required')
    try:
        data = base64.urlsafe_b64decode(authorization.removeprefix('Bearer ')).decode()
        identity, expiry, signature = data.split(':')
        actual = hmac.new(settings.session_secret.encode(), f'{identity}:{expiry}'.encode(), hashlib.sha256).hexdigest()
        if identity != 'demo-user' or int(expiry) <= time.time() or not hmac.compare_digest(actual, signature):
            raise ValueError('invalid token')
    except (ValueError, UnicodeError, base64.binascii.Error):
        raise HTTPException(401, detail='Invalid demo session') from None
    return identity


def admin_auth(x_admin_key: Annotated[str | None, Header()] = None) -> bool:
    if not settings.demo_mode:
        raise HTTPException(503, detail='Production admin panel is not configured')
    if not settings.admin_key or not x_admin_key or not hmac.compare_digest(settings.admin_key, x_admin_key):
        raise HTTPException(401, detail='Admin key required')
    return True


def require_vehicle(session: Session, vehicle_id: str, *, public_only: bool = False) -> Vehicle:
    vehicle = session.get(Vehicle, vehicle_id)
    if vehicle is None or (public_only and not vehicle.is_published):
        raise HTTPException(404, detail='Vehicle not found')
    return vehicle


def is_blocked(session: Session, vehicle_id: str, start: datetime, end: datetime) -> bool:
    now = datetime.now(timezone.utc).replace(tzinfo=None)
    overlapping = session.scalars(select(Booking).where(
        Booking.vehicle_id == vehicle_id,
        Booking.start_at < utc_naive(end), Booking.end_at > utc_naive(start),
        Booking.status.in_(['PENDING', 'CONFIRMED', 'ACTIVE'])
    )).all()
    return any(b.status != 'PENDING' or b.expires_at > now for b in overlapping)


@app.get('/health')
def health():
    return {'status': 'ok', 'demoMode': settings.demo_mode}


@app.get('/v1/vehicles')
def list_vehicles(page: int = Query(1, ge=1), pageSize: int = Query(20, ge=1, le=100)):
    with Session(engine) as session:
        vehicles = session.scalars(select(Vehicle).where(Vehicle.is_published.is_(True)).order_by(Vehicle.name)
                                   .offset((page - 1) * pageSize).limit(pageSize)).all()
        return {'data': [vehicle_out(v) for v in vehicles], 'page': page,
                'pageSize': pageSize, 'isDemo': settings.demo_mode}


@app.get('/v1/map/vehicles')
def map_vehicles():
    # Only display location markers when an admin supplied BOTH coordinates.
    # GPS telemetry is not connected; these are static operator-confirmed positions.
    with Session(engine) as session:
        vehicles = session.scalars(select(Vehicle).where(
            Vehicle.is_published.is_(True), Vehicle.latitude.is_not(None),
            Vehicle.longitude.is_not(None)).order_by(Vehicle.name)).all()
        return {'data': [vehicle_out(v) for v in vehicles], 'isDemo': settings.demo_mode,
                'positionSource': 'ADMIN_STATIC'}


@app.get('/v1/vehicles/{vehicle_id}')
def get_vehicle(vehicle_id: str):
    with Session(engine) as session:
        return vehicle_out(require_vehicle(session, vehicle_id, public_only=True))


@app.post('/v1/quotes')
def quote(payload: BookingInput):
    with Session(engine) as session:
        if expire_pending_bookings(session):
            session.commit()
        v = require_vehicle(session, payload.vehicleId, public_only=True)
        return {'vehicleId': v.id, 'totalRub': calculate_price(v, payload.startAt, payload.endAt),
                'currency': 'RUB', 'isAvailable': not is_blocked(session, v.id, payload.startAt, payload.endAt),
                'isDemo': settings.demo_mode}


@app.post('/v1/demo/session')
def create_demo_session():
    if not settings.demo_mode:
        raise HTTPException(404, detail='Not found')
    return {'accessToken': demo_token(), 'expiresInSec': 86400, 'isDemo': True}


@app.get('/v1/bookings')
def my_bookings(user: str = Depends(demo_auth), page: int = Query(1, ge=1),
                pageSize: int = Query(20, ge=1, le=100)):
    with Session(engine) as session:
        if expire_pending_bookings(session):
            session.commit()
        bookings = session.scalars(select(Booking).where(Booking.user_id == user)
                                   .order_by(Booking.created_at.desc())
                                   .offset((page - 1) * pageSize).limit(pageSize)).all()
        return {'data': [booking_out(b) for b in bookings], 'page': page, 'pageSize': pageSize}


@app.get('/v1/bookings/{booking_id}/timeline')
def booking_timeline(booking_id: str, user: str = Depends(demo_auth)):
    with Session(engine) as session:
        if expire_pending_bookings(session):
            session.commit()
        booking = session.get(Booking, booking_id)
        if booking is None or booking.user_id != user:
            raise HTTPException(404, detail='Booking not found')
        events = session.scalars(select(BookingEvent).where(BookingEvent.booking_id == booking_id)
                                 .order_by(BookingEvent.occurred_at, BookingEvent.id)).all()
        return {'bookingId': booking.id, 'status': booking.status, 'isDemo': True,
                'events': [{'type': event.event_type,
                            'at': event.occurred_at.replace(tzinfo=timezone.utc).isoformat()}
                           for event in events]}


@app.post('/v1/bookings', status_code=status.HTTP_201_CREATED)
def create_booking(payload: BookingInput, user: str = Depends(demo_auth),
                   idempotency_key: Annotated[str | None, Header(min_length=8, max_length=128)] = None):
    # Demo-only SQLite lock prevents overlapping reservations in concurrent demo requests.
    # Production must use Postgres row locking + a database-level range exclusion rule.
    with engine.connect() as conn:
        conn.exec_driver_sql('BEGIN IMMEDIATE')
        with Session(bind=conn) as session:
            expire_pending_bookings(session)
            request_hash = hashlib.sha256(f'{payload.vehicleId}|{utc_naive(payload.startAt).isoformat()}|{utc_naive(payload.endAt).isoformat()}'.encode()).hexdigest()
            if idempotency_key:
                previous = session.scalar(select(Booking).where(Booking.user_id == user, Booking.idempotency_key == idempotency_key))
                if previous:
                    if previous.idempotency_hash != request_hash:
                        raise HTTPException(422, detail='Idempotency key reused with a different booking')
                    result = booking_out(previous)
                    session.flush()
                    conn.commit()
                    return result
            v = require_vehicle(session, payload.vehicleId, public_only=True)
            if is_blocked(session, v.id, payload.startAt, payload.endAt):
                raise HTTPException(409, detail='Vehicle already reserved for this period')
            now = datetime.now(timezone.utc).replace(tzinfo=None)
            booking = Booking(id=str(uuid.uuid4()), user_id=user, vehicle_id=v.id,
                              start_at=utc_naive(payload.startAt), end_at=utc_naive(payload.endAt),
                              total_rub=calculate_price(v, payload.startAt, payload.endAt),
                              status='PENDING', created_at=now, expires_at=now + timedelta(minutes=20),
                              idempotency_key=idempotency_key, idempotency_hash=request_hash if idempotency_key else None)
            session.add(booking)
            session.flush()
            append_booking_event(session, booking.id, 'CREATED')
            session.flush()
            result = booking_out(booking)
        conn.commit()
        return result


@app.post('/v1/bookings/{booking_id}/cancel')
def cancel_booking(booking_id: str, user: str = Depends(demo_auth)):
    with Session(engine) as session:
        if expire_pending_bookings(session):
            session.commit()
        booking = session.get(Booking, booking_id)
        if not booking or booking.user_id != user:
            raise HTTPException(404, detail='Booking not found')
        if booking.status not in ('PENDING', 'CONFIRMED', 'CANCELLED'):
            raise HTTPException(409, detail='Booking can no longer be cancelled automatically')
        if booking.status != 'CANCELLED':
            booking.status = 'CANCELLED'
            append_booking_event(session, booking.id, 'CANCELLED')
        session.commit()
        return booking_out(booking)


@app.get('/v1/admin/bookings')
def admin_bookings(_: bool = Depends(admin_auth)):
    with Session(engine) as session:
        if expire_pending_bookings(session):
            session.commit()
        bookings = session.scalars(select(Booking).order_by(Booking.created_at.desc()).limit(100)).all()
        return {'data': [booking_out(b) for b in bookings]}


@app.post('/v1/admin/bookings/{booking_id}/confirm')
def confirm_booking(booking_id: str, _: bool = Depends(admin_auth)):
    with Session(engine) as session:
        if expire_pending_bookings(session):
            session.commit()
        booking = session.get(Booking, booking_id)
        if not booking:
            raise HTTPException(404, detail='Booking not found')
        if booking.status != 'PENDING' or booking.expires_at <= datetime.now(timezone.utc).replace(tzinfo=None):
            raise HTTPException(409, detail='Only unexpired pending bookings can be confirmed')
        booking.status = 'CONFIRMED'
        append_booking_event(session, booking.id, 'CONFIRMED')
        session.commit()
        return booking_out(booking)


@app.post('/v1/admin/bookings/{booking_id}/handover')
def demo_handover_booking(booking_id: str, _: bool = Depends(admin_auth)):
    """Record *manual* demo handover; NEVER remotely unlocks a vehicle."""
    return transition_demo_booking(booking_id, expected='CONFIRMED', target='ACTIVE', event='HANDOVER')


@app.post('/v1/admin/bookings/{booking_id}/return')
def demo_return_booking(booking_id: str, _: bool = Depends(admin_auth)):
    """Record manual demo return; this does not settle payment or control doors."""
    return transition_demo_booking(booking_id, expected='ACTIVE', target='COMPLETED', event='RETURNED')


# The demo admin API is intentionally disabled in production mode. It uses
# a single shared key; before launch replace it with audited roles and MFA.
@app.get('/admin')
def admin_page():
    if not settings.demo_mode:
        raise HTTPException(404, detail='Not found')
    return FileResponse(Path(__file__).with_name('admin.html'), media_type='text/html',
                        headers={'Cache-Control': 'no-store',
                                 'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; img-src https: data:; object-src 'none'; base-uri 'none'"})


@app.get('/v1/admin/vehicles')
def admin_list_vehicles(_: bool = Depends(admin_auth)):
    with Session(engine) as session:
        rows = session.scalars(select(Vehicle).order_by(Vehicle.name)).all()
        return {'data': [vehicle_out(v) for v in rows], 'isDemo': True}


@app.get('/v1/admin/vehicles/{vehicle_id}')
def admin_get_vehicle(vehicle_id: str, _: bool = Depends(admin_auth)):
    with Session(engine) as session:
        return vehicle_out(require_vehicle(session, vehicle_id))


@app.post('/v1/admin/vehicles', status_code=status.HTTP_201_CREATED)
def admin_create_vehicle(payload: FleetBase, _: bool = Depends(admin_auth)):
    with Session(engine) as session:
        vehicle = Vehicle(id=str(uuid.uuid4()))
        apply_vehicle_fields(vehicle, payload.model_dump())
        session.add(vehicle)
        session.commit()
        return vehicle_out(vehicle)


@app.patch('/v1/admin/vehicles/{vehicle_id}')
def admin_update_vehicle(vehicle_id: str, payload: FleetPatch, _: bool = Depends(admin_auth)):
    with Session(engine) as session:
        vehicle = require_vehicle(session, vehicle_id)
        apply_vehicle_fields(vehicle, payload.model_dump(exclude_unset=True))
        session.commit()
        return vehicle_out(vehicle)


@app.get('/admin.js')
def admin_script():
    if not settings.demo_mode:
        raise HTTPException(404, detail='Not found')
    return FileResponse(Path(__file__).with_name('admin.js'), media_type='text/javascript',
                        headers={'Cache-Control': 'no-store'})
