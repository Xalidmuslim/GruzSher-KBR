from datetime import datetime, timedelta, timezone
import os
import pytest
from fastapi.testclient import TestClient

os.environ['CARGOSHARE_DEMO_MODE'] = 'true'
os.environ['CARGOSHARE_ADMIN_KEY'] = 'test-admin-key'
os.environ['CARGOSHARE_SESSION_SECRET'] = 'test-session-secret-long'
os.environ['CARGOSHARE_DATABASE_URL'] = 'sqlite://'

from app.main import app, reset_demo_db  # noqa: E402


@pytest.fixture()
def client():
    reset_demo_db()
    with TestClient(app) as client:
        yield client


def time_window(hours=4):
    start = (datetime.now(timezone.utc) + timedelta(days=2)).replace(minute=0, second=0, microsecond=0)
    end = start + timedelta(hours=hours)
    return start.isoformat(), end.isoformat()


def login(client):
    response = client.post('/v1/demo/session')
    assert response.status_code == 200
    return {'Authorization': f"Bearer {response.json()['accessToken']}"}


def test_catalog_seed_data_are_explicitly_demo(client):
    response = client.get('/v1/vehicles')
    assert response.status_code == 200
    assert response.json()['isDemo'] is True
    assert len(response.json()['data']) >= 2
    assert all(v['dailyPriceRub'] > 0 for v in response.json()['data'])


def test_four_demo_trucks_include_independent_second_gazelle(client):
    response = client.get('/v1/vehicles')
    assert response.status_code == 200
    cars = response.json()['data']
    assert len(cars) == 4
    assert len({car['id'] for car in cars}) == 4
    extra = next(car for car in cars if car['id'] == 'gazelle-next-2')
    original = next(car for car in cars if car['id'] == 'gazelle-next')
    assert extra['name'] == 'ГАЗель NEXT №2'
    assert extra['hourlyPriceRub'] == original['hourlyPriceRub']
    assert extra['latitude'] == 43.4824
    assert extra['longitude'] == 43.5856


def test_booking_requires_authentication(client):
    start, end = time_window()
    response = client.post('/v1/bookings', json={'vehicleId': 'gazelle-next', 'startAt': start, 'endAt': end})
    assert response.status_code == 401


def test_quote_calculation_and_booking(client):
    headers = login(client)
    start, end = time_window(4)
    quote = client.post('/v1/quotes', json={'vehicleId': 'gazelle-next', 'startAt': start, 'endAt': end})
    assert quote.status_code == 200
    assert quote.json()['totalRub'] > 0
    response = client.post('/v1/bookings', headers=headers, json={'vehicleId': 'gazelle-next', 'startAt': start, 'endAt': end})
    assert response.status_code == 201
    booking = response.json()
    assert booking['status'] == 'PENDING'
    assert booking['totalRub'] == quote.json()['totalRub']
    mine = client.get('/v1/bookings', headers=headers)
    assert mine.status_code == 200 and len(mine.json()['data']) == 1


def test_overlap_blocked_cancellation_releases_slot(client):
    headers = login(client)
    start, end = time_window()
    body = {'vehicleId': 'gazelle-next', 'startAt': start, 'endAt': end}
    first = client.post('/v1/bookings', headers=headers, json=body)
    assert first.status_code == 201
    assert client.post('/v1/bookings', headers=headers, json=body).status_code == 409
    cancel = client.post(f"/v1/bookings/{first.json()['id']}/cancel", headers=headers)
    assert cancel.status_code == 200 and cancel.json()['status'] == 'CANCELLED'
    assert client.post('/v1/bookings', headers=headers, json=body).status_code == 201


def test_invalid_window_rejected(client):
    start, end = time_window()
    bad = client.post('/v1/quotes', json={'vehicleId': 'gazelle-next', 'startAt': end, 'endAt': start})
    assert bad.status_code == 422
    bad_timezone = client.post('/v1/quotes', json={'vehicleId': 'gazelle-next', 'startAt': '2026-11-10T10:00:00', 'endAt': '2026-11-10T12:00:00'})
    assert bad_timezone.status_code == 422


def test_admin_needs_key_and_can_confirm(client):
    headers = login(client)
    start, end = time_window()
    booking = client.post('/v1/bookings', headers=headers, json={'vehicleId': 'gazelle-next', 'startAt': start, 'endAt': end}).json()
    assert client.post(f"/v1/admin/bookings/{booking['id']}/confirm").status_code == 401
    response = client.post(f"/v1/admin/bookings/{booking['id']}/confirm", headers={'X-Admin-Key': 'test-admin-key'})
    assert response.status_code == 200 and response.json()['status'] == 'CONFIRMED'
    assert client.post(f"/v1/bookings/{booking['id']}/cancel", headers=headers).status_code == 200


def test_overlapping_cars_are_independent(client):
    headers = login(client)
    start, end = time_window()
    for car in ['gazelle-next', 'ford-transit']:
        assert client.post('/v1/bookings', headers=headers, json={'vehicleId': car, 'startAt': start, 'endAt': end}).status_code == 201


def test_demo_session_is_closed_if_disabled(client, monkeypatch):
    from app import main
    monkeypatch.setattr(main.settings, 'demo_mode', False)
    assert client.post('/v1/demo/session').status_code == 404


def test_idempotent_booking_retry(client):
    headers = {**login(client), 'Idempotency-Key': 'rental-intent-001'}
    start, end = time_window(24)
    payload = {'vehicleId': 'gazelle-next', 'startAt': start, 'endAt': end}
    first = client.post('/v1/bookings', headers=headers, json=payload)
    assert first.status_code == 201
    retry = client.post('/v1/bookings', headers=headers, json=payload)
    assert retry.status_code == 201 and retry.json()['id'] == first.json()['id']
    changed = client.post('/v1/bookings', headers=headers, json={**payload, 'vehicleId': 'ford-transit'})
    assert changed.status_code == 422

def test_day_price_cap(client):
    start, end = time_window(24)
    amount = client.post('/v1/quotes', json={'vehicleId': 'gazelle-next', 'startAt': start, 'endAt': end}).json()['totalRub']
    assert amount == 3400

# Editable fleet: tests first. The mobile catalog must reflect changes
# without an Android/iOS rebuild, while historic bookings remain intact.
ADMIN = {'X-Admin-Key': 'test-admin-key'}


def test_admin_vehicle_edit_requires_secret_and_updates_public_catalog(client):
    body = {'name': 'ГАЗель NEXT — обновлено', 'dailyPriceRub': 4600,
            'pickupLocation': 'Нальчик · тестовая точка 2'}
    assert client.patch('/v1/admin/vehicles/gazelle-next', json=body).status_code == 401
    changed = client.patch('/v1/admin/vehicles/gazelle-next', headers=ADMIN, json=body)
    assert changed.status_code == 200
    assert changed.json()['name'] == body['name']
    catalog = client.get('/v1/vehicles').json()['data']
    assert next(v for v in catalog if v['id'] == 'gazelle-next')['dailyPriceRub'] == 4600
    assert client.get('/v1/admin/vehicles', headers=ADMIN).status_code == 200


def test_admin_vehicle_update_survives_seeding_and_existing_booking_price_snapshot(client):
    start, end = time_window(24)
    booking = client.post('/v1/bookings', headers=login(client),
                          json={'vehicleId': 'gazelle-next', 'startAt': start, 'endAt': end}).json()
    assert booking['totalRub'] == 3400
    assert client.patch('/v1/admin/vehicles/gazelle-next', headers=ADMIN,
                        json={'dailyPriceRub': 4500}).status_code == 200
    from app.main import seed
    seed()  # restarting/re-seeding must not overwrite administrator edits
    updated = client.get('/v1/vehicles/gazelle-next').json()
    assert updated['dailyPriceRub'] == 4500
    bookings = client.get('/v1/bookings', headers=login(client)).json()['data']
    assert bookings[0]['totalRub'] == 3400


def test_admin_can_create_demo_vehicle_and_control_publication(client):
    payload = {'name': 'Тестовый бортовой грузовик', 'type': 'Бортовой',
               'payloadKg': 1400, 'cargoVolumeM3': 12, 'hourlyPriceRub': 600,
               'dailyPriceRub': 3900, 'transmission': 'Механика',
               'pickupLocation': 'Нальчик · тестовый пункт'}
    created = client.post('/v1/admin/vehicles', headers=ADMIN, json=payload)
    assert created.status_code == 201, created.text
    car_id = created.json()['id']
    assert car_id in [v['id'] for v in client.get('/v1/vehicles').json()['data']]
    hidden = client.patch(f'/v1/admin/vehicles/{car_id}', headers=ADMIN,
                          json={'isPublished': False})
    assert hidden.status_code == 200
    assert car_id not in [v['id'] for v in client.get('/v1/vehicles').json()['data']]
    assert client.get(f'/v1/vehicles/{car_id}').status_code == 404
    assert client.get(f'/v1/admin/vehicles/{car_id}', headers=ADMIN).json()['isPublished'] is False
    start, end = time_window()
    assert client.post('/v1/quotes', json={'vehicleId': car_id, 'startAt': start, 'endAt': end}).status_code == 404
    assert client.post('/v1/bookings', headers=login(client),
                       json={'vehicleId': car_id, 'startAt': start, 'endAt': end}).status_code == 404
    assert client.patch(f'/v1/admin/vehicles/{car_id}', headers=ADMIN,
                        json={'isPublished': True}).status_code == 200
    assert client.get(f'/v1/vehicles/{car_id}').status_code == 200


def test_admin_validation_rejects_invalid_edit(client):
    invalid = [
        {'dailyPriceRub': -1}, {'hourlyPriceRub': 0}, {'payloadKg': 0},
        {'name': ''}, {'isDemo': False}, {'id': 'another-id'},
        {'pickupLocation': ' '}, {'unknown': 'unexpected'}, {},
    ]
    for patch in invalid:
        response = client.patch('/v1/admin/vehicles/gazelle-next', headers=ADMIN, json=patch)
        assert response.status_code == 422, (patch, response.text)
    assert client.get('/v1/vehicles/gazelle-next').json()['dailyPriceRub'] == 3400


def test_unpublish_preserves_historical_booking(client):
    start, end = time_window()
    booking = client.post('/v1/bookings', headers=login(client),
                          json={'vehicleId': 'gazelle-next', 'startAt': start, 'endAt': end}).json()
    assert client.patch('/v1/admin/vehicles/gazelle-next', headers=ADMIN,
                        json={'isPublished': False}).status_code == 200
    assert client.get('/v1/bookings', headers=login(client)).json()['data'][0]['id'] == booking['id']
    assert client.get('/v1/admin/vehicles/gazelle-next', headers=ADMIN).status_code == 200


def test_demo_admin_page_available_only_in_demo(client):
    page = client.get('/admin')
    assert page.status_code == 200
    assert 'Редактирование автопарка' in page.text
    assert client.get('/admin.js').status_code == 200
    from app import main
    old = main.settings.demo_mode
    main.settings.demo_mode = False
    try:
        assert client.get('/admin').status_code == 404
    finally:
        main.settings.demo_mode = old

# v2: optional operator-confirmed vehicle positions, never invented on mobile.
def test_map_contains_only_positioned_public_vehicles(client):
    result = client.get('/v1/map/vehicles')
    assert result.status_code == 200
    assert result.json()['data'] == []
    assert result.json()['isDemo'] is True


def test_admin_coordinates_are_atomic_and_exposed_on_public_map(client):
    route = '/v1/admin/vehicles/gazelle-next'
    assert client.patch(route, headers=ADMIN, json={'latitude': 43.48}).status_code == 422
    assert client.patch(route, headers=ADMIN, json={'latitude': 96.0, 'longitude': 43.60}).status_code == 422
    changed = client.patch(route, headers=ADMIN, json={'latitude': 43.4853, 'longitude': 43.6071})
    assert changed.status_code == 200, changed.text
    assert changed.json()['latitude'] == pytest.approx(43.4853)
    positions = client.get('/v1/map/vehicles').json()['data']
    assert len(positions) == 1
    assert positions[0]['id'] == 'gazelle-next'
    assert positions[0]['longitude'] == pytest.approx(43.6071)


def test_map_does_not_expose_hidden_vehicles(client):
    route = '/v1/admin/vehicles/gazelle-next'
    client.patch(route, headers=ADMIN, json={'latitude': 43.4853, 'longitude': 43.6071})
    assert client.patch(route, headers=ADMIN, json={'isPublished': False}).status_code == 200
    assert client.get('/v1/map/vehicles').json()['data'] == []


def test_map_coordinates_can_be_cleared_together(client):
    route = '/v1/admin/vehicles/gazelle-next'
    client.patch(route, headers=ADMIN, json={'latitude': 43.4853, 'longitude': 43.6071})
    response = client.patch(route, headers=ADMIN, json={'latitude': None, 'longitude': None})
    assert response.status_code == 200, response.text
    assert client.get('/v1/map/vehicles').json()['data'] == []

# v2.1: controlled, demo-only manual handover/return. No remote unlocking.
def test_demo_rental_requires_confirmed_booking_and_admin_for_handover(client):
    start, end = time_window()
    user = login(client)
    booking = client.post('/v1/bookings', headers=user,
                          json={'vehicleId': 'gazelle-next', 'startAt': start, 'endAt': end}).json()
    bid = booking['id']
    assert client.post(f'/v1/admin/bookings/{bid}/handover').status_code == 401
    assert client.post(f'/v1/admin/bookings/{bid}/handover', headers=ADMIN).status_code == 409
    assert client.post(f'/v1/admin/bookings/{bid}/return', headers=ADMIN).status_code == 409
    assert client.post(f'/v1/admin/bookings/{bid}/confirm', headers=ADMIN).status_code == 200
    handed = client.post(f'/v1/admin/bookings/{bid}/handover', headers=ADMIN)
    assert handed.status_code == 200 and handed.json()['status'] == 'ACTIVE'
    assert client.post(f'/v1/bookings/{bid}/cancel', headers=user).status_code == 409
    assert client.post(f'/v1/admin/bookings/{bid}/handover', headers=ADMIN).status_code == 409
    returned = client.post(f'/v1/admin/bookings/{bid}/return', headers=ADMIN)
    assert returned.status_code == 200 and returned.json()['status'] == 'COMPLETED'
    assert client.post(f'/v1/admin/bookings/{bid}/return', headers=ADMIN).status_code == 409
    assert client.get('/v1/bookings', headers=user).json()['data'][0]['status'] == 'COMPLETED'


def test_rental_timeline_privacy_and_monotonic_events(client):
    user = login(client)
    start, end = time_window()
    b = client.post('/v1/bookings', headers=user,
                    json={'vehicleId': 'gazelle-next', 'startAt': start, 'endAt': end}).json()
    path = f"/v1/bookings/{b['id']}/timeline"
    assert client.get(path).status_code == 401
    timeline = client.get(path, headers=user)
    assert timeline.status_code == 200
    assert [evt['type'] for evt in timeline.json()['events']] == ['CREATED']
    assert client.post(f"/v1/admin/bookings/{b['id']}/confirm", headers=ADMIN).status_code == 200
    assert client.post(f"/v1/admin/bookings/{b['id']}/handover", headers=ADMIN).status_code == 200
    assert client.post(f"/v1/admin/bookings/{b['id']}/return", headers=ADMIN).status_code == 200
    events = client.get(path, headers=user).json()['events']
    assert [evt['type'] for evt in events] == ['CREATED', 'CONFIRMED', 'HANDOVER', 'RETURNED']
    assert all('at' in evt for evt in events)
    assert all(evt['at'].endswith('+00:00') for evt in events)
    assert client.get('/v1/bookings/does-not-exist/timeline', headers=user).status_code == 404


def test_cancelled_booking_cannot_be_handed_over(client):
    user = login(client)
    start, end = time_window()
    b = client.post('/v1/bookings', headers=user,
                    json={'vehicleId': 'ford-transit', 'startAt': start, 'endAt': end}).json()
    assert client.post(f"/v1/bookings/{b['id']}/cancel", headers=user).status_code == 200
    assert client.post(f"/v1/admin/bookings/{b['id']}/confirm", headers=ADMIN).status_code == 409
    assert client.post(f"/v1/admin/bookings/{b['id']}/handover", headers=ADMIN).status_code == 409
    assert [v['type'] for v in client.get(f"/v1/bookings/{b['id']}/timeline", headers=user).json()['events']] == ['CREATED', 'CANCELLED']


def test_production_mode_disables_rental_admin_demo_endpoints(client):
    from app import main
    previous = main.settings.demo_mode
    main.settings.demo_mode = False
    try:
        assert client.post('/v1/admin/bookings/abc/handover', headers=ADMIN).status_code == 503
        assert client.post('/v1/admin/bookings/abc/return', headers=ADMIN).status_code == 503
    finally:
        main.settings.demo_mode = previous


def test_expired_pending_booking_is_visible_as_expired_and_releases_slot(client):
    from sqlalchemy.orm import Session
    from app.main import engine, Booking

    headers = login(client)
    start, end = time_window()
    payload = {'vehicleId': 'gazelle-next', 'startAt': start, 'endAt': end}
    booked = client.post('/v1/bookings', headers=headers, json=payload, ).json()
    with Session(engine) as session:
        row = session.get(Booking, booked['id'])
        row.expires_at = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(seconds=1)
        session.commit()

    mine = client.get('/v1/bookings', headers=headers)
    assert mine.status_code == 200
    assert mine.json()['data'][0]['status'] == 'EXPIRED'
    timeline = client.get(f"/v1/bookings/{booked['id']}/timeline", headers=headers).json()['events']
    assert [event['type'] for event in timeline] == ['CREATED', 'EXPIRED']
    assert client.get('/v1/bookings', headers=headers).status_code == 200
    timeline_again = client.get(f"/v1/bookings/{booked['id']}/timeline", headers=headers).json()['events']
    assert [event['type'] for event in timeline_again] == ['CREATED', 'EXPIRED']
    assert client.post('/v1/quotes', json=payload).json()['isAvailable'] is True
    assert client.post('/v1/bookings', headers=headers, json=payload).status_code == 201
    assert client.post(f"/v1/bookings/{booked['id']}/cancel", headers=headers).status_code == 409
    assert client.post(f"/v1/admin/bookings/{booked['id']}/confirm", headers={'X-Admin-Key': 'test-admin-key'}).status_code == 409


def test_expired_booking_idempotency_retry_does_not_create_duplicate(client):
    from sqlalchemy.orm import Session
    from app.main import engine, Booking

    headers = {**login(client), 'Idempotency-Key': 'idempotent-expired-booking'}
    start, end = time_window()
    payload = {'vehicleId': 'gazelle-next', 'startAt': start, 'endAt': end}
    original = client.post('/v1/bookings', headers=headers, json=payload).json()
    with Session(engine) as session:
        row = session.get(Booking, original['id'])
        row.expires_at = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(minutes=2)
        session.commit()
    replay = client.post('/v1/bookings', headers=headers, json=payload)
    assert replay.status_code == 201
    assert replay.json()['id'] == original['id']
    assert replay.json()['status'] == 'EXPIRED'
