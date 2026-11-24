import os
import tempfile

_tmp = tempfile.mkdtemp()
os.environ["DATABASE_URL"] = f"sqlite:///{_tmp}/test.db"
os.environ["UPLOAD_DIR"] = f"{_tmp}/uploads"

from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402


def auth(token):
    return {"Authorization": f"Bearer {token}"}


def test_full_flow():
    with TestClient(app) as c:
        # landlord signs in
        r = c.post("/auth/login", json={"phone": "0700000000", "password": "changeme123"})
        assert r.status_code == 200, r.text
        L = auth(r.json()["access_token"])
        assert c.post("/auth/login", json={"phone": "0700000000", "password": "nope"}).status_code == 401

        # register tenant
        body = {"name": "Mary Mutua", "phone": "+254 712 345 678", "unit_number": "b4",
                "move_in_date": "2026-11-01", "initial_meter_reading": 120.5}
        r = c.post("/tenants", json=body, headers=L)
        assert r.status_code == 201, r.text
        created = r.json()
        tid = created["tenant"]["tenancy_id"]
        assert created["tenant"]["unit_number"] == "B4"
        assert created["tenant"]["phone"] == "0712345678"
        assert c.post("/tenants", json=body, headers=L).status_code == 409  # duplicate

        # tenant signs in
        r = c.post("/auth/login", json={"phone": "0712345678", "password": created["temporary_password"]})
        assert r.status_code == 200
        T = auth(r.json()["access_token"])
        assert c.get("/tenants", headers=T).status_code == 403

        # readings: lower rejected, big jump needs confirm, photo upload works
        def save(value, **extra):
            return c.post("/readings", data={"tenancy_id": tid, "value": value, **extra}, headers=L)
        assert save(100).status_code == 422
        assert save(300).status_code == 409
        assert save(300, confirm_large_jump="true").status_code == 201
        r = c.post("/readings", data={"tenancy_id": tid, "value": 305.25}, headers=L,
                   files={"photo": ("m.jpg", b"\xff\xd8fakejpeg", "image/jpeg")})
        assert r.status_code == 201, r.text
        assert r.json()["units_used"] == 5.25 and r.json()["has_photo"] is True
        scan_status = c.post("/readings/scan", files={"photo": ("m.jpg", b"x", "image/jpeg")}, headers=L).status_code
        # With deployed models, the fake image is rejected as an invalid image; without
        # deployed models, the endpoint clearly reports that model files are missing.
        assert scan_status in (422, 503)
        assert c.post("/readings/scan", files={"photo": ("m.jpg", b"x", "image/jpeg")}, headers=T).status_code == 403

        usage = c.get("/readings/mine", headers=T).json()
        assert usage["latest_reading"] == 305.25 and len(usage["readings"]) == 2
        assert c.get(f"/tenants/{tid}/readings", headers=T).status_code == 200

        # maintenance
        r = c.post("/requests", json={"title": "Leaking tap", "description": "Kitchen tap drips", "urgency": "HIGH"}, headers=T)
        assert r.status_code == 201
        rid = r.json()["id"]
        assert len(c.get("/requests", headers=T).json()) == 1
        r = c.patch(f"/requests/{rid}/status", json={"status": "IN_PROGRESS", "landlord_note": "Plumber Tuesday"}, headers=L)
        assert r.json()["status"] == "IN_PROGRESS"
        assert c.patch(f"/requests/{rid}/status", json={"status": "RESOLVED"}, headers=T).status_code == 403

        # announcements
        r = c.post("/announcements", json={"title": "Water off Friday", "body": "8am to 2pm", "category": "WATER"}, headers=L)
        assert r.status_code == 201
        assert c.post("/announcements", json={"title": "x y z", "body": "nope"}, headers=T).status_code == 403
        assert len(c.get("/announcements", headers=T).json()) == 1
        assert c.delete(f"/announcements/{r.json()['id']}", headers=L).status_code == 204

        # overview + move out
        ov = c.get("/overview", headers=L).json()
        assert ov["tenants"] == 1 and ov["open_requests"] == 1 and ov["high_urgency_open"] == 1
        assert c.patch(f"/tenants/{tid}/move-out", headers=L).status_code == 200
        assert c.post("/auth/login", json={"phone": "0712345678", "password": created["temporary_password"]}).status_code == 403


def test_photos_offline_timestamps_devices_and_passwords():
    with TestClient(app) as c:
        L = auth(c.post("/auth/login", json={"phone": "0700000000", "password": "changeme123"}).json()["access_token"])
        r = c.post("/tenants", headers=L, json={"name": "Peter Musyoka", "phone": "0722111222", "unit_number": "C3",
                   "move_in_date": "2026-11-01", "initial_meter_reading": 10, "password": "secret12"})
        tid = r.json()["tenant"]["tenancy_id"]
        T = auth(c.post("/auth/login", json={"phone": "0722111222", "password": "secret12"}).json()["access_token"])
        other = c.post("/tenants", headers=L, json={"name": "Ann Wanza", "phone": "0733111222", "unit_number": "C4",
                       "move_in_date": "2026-11-01", "initial_meter_reading": 5, "password": "secret12"}).json()
        O = auth(c.post("/auth/login", json={"phone": "0733111222", "password": "secret12"}).json()["access_token"])

        # reading queued offline keeps its own timestamp and photo
        r = c.post("/readings", headers=L, data={"tenancy_id": tid, "value": 14, "recorded_at": "2026-11-05T08:30:00Z"},
                   files={"photo": ("m.jpg", b"\xff\xd8photo", "image/jpeg")})
        assert r.status_code == 201, r.text
        first = r.json()
        assert first["created_at"].startswith("2026-11-05T08:30:00")
        # a timestamp in the future is clamped
        r = c.post("/readings", headers=L, data={"tenancy_id": tid, "value": 15, "recorded_at": "2099-01-01T00:00:00Z"})
        second = r.json()
        assert not second["created_at"].startswith("2099")

        # photo access: landlord and owner yes, other tenant no, no-photo reading 404
        assert c.get(f"/readings/{first['id']}/photo", headers=L).content == b"\xff\xd8photo"
        assert c.get(f"/readings/{first['id']}/photo", headers=T).status_code == 200
        assert c.get(f"/readings/{first['id']}/photo", headers=O).status_code == 403
        assert c.get(f"/readings/{second['id']}/photo", headers=L).status_code == 404

        # only the latest reading can be deleted
        assert c.delete(f"/readings/{first['id']}", headers=L).status_code == 409
        assert c.delete(f"/readings/{second['id']}", headers=T).status_code == 403
        assert c.delete(f"/readings/{second['id']}", headers=L).status_code == 204
        assert c.get("/readings/mine", headers=T).json()["latest_reading"] == 14

        # devices
        tok = {"token": "fcm-token-1234567890"}
        assert c.post("/devices", json=tok, headers=T).status_code == 204
        assert c.post("/devices", json=tok, headers=O).status_code == 204   # moves to the newer user
        assert c.post("/devices/unregister", json=tok, headers=T).status_code == 204  # not theirs: no-op
        assert c.post("/devices/unregister", json=tok, headers=O).status_code == 204

        # push helpers run without Firebase configured
        assert c.post("/announcements", headers=L, json={"title": "Garbage day", "body": "Tuesday 7am", "category": "GARBAGE"}).status_code == 201

        # change password
        assert c.post("/auth/change-password", headers=T, json={"current_password": "wrong", "new_password": "newpass1"}).status_code == 422
        assert c.post("/auth/change-password", headers=T, json={"current_password": "secret12", "new_password": "newpass1"}).status_code == 204
        assert c.post("/auth/login", json={"phone": "0722111222", "password": "secret12"}).status_code == 401
        assert c.post("/auth/login", json={"phone": "0722111222", "password": "newpass1"}).status_code == 200


def test_profile_lease_bills_notifications_dashboard():
    with TestClient(app) as c:
        L = auth(c.post("/auth/login", json={"phone": "0700000000", "password": "changeme123"}).json()["access_token"])
        r = c.post("/tenants", headers=L, json={"name": "Lucy Mwende", "phone": "0744555666", "unit_number": "D1",
                   "move_in_date": "2026-10-01", "initial_meter_reading": 50, "password": "secret12",
                   "monthly_rent": 6500, "deposit": 6500, "rent_due_day": 5})
        assert r.status_code == 201, r.text
        tid = r.json()["tenant"]["tenancy_id"]
        T = auth(c.post("/auth/login", json={"phone": "0744555666", "password": "secret12"}).json()["access_token"])

        # profiles
        me = c.get("/me", headers=T).json()
        assert me["tenant"]["unit_number"] == "D1" and me["tenant"]["monthly_rent"] == 6500
        r = c.patch("/me", headers=T, json={"email": "lucy@example.com", "emergency_contact": "Mum 0700111222"})
        assert r.json()["email"] == "lucy@example.com" and r.json()["tenant"]["email"] == "lucy@example.com"
        assert c.patch("/me", headers=T, json={"email": "not-an-email"}).status_code == 422
        assert c.patch("/me", headers=T, json={"email": ""}).json()["email"] is None
        assert c.patch("/estate", headers=T, json={"estate_name": "x y"}).status_code == 403
        r = c.patch("/estate", headers=L, json={"estate_name": "Haven Heights", "address": "Machakos town",
                    "payment_info": "M-Pesa Paybill 123456, account = unit number"})
        assert r.status_code == 200 and r.json()["landlord_phone"] == "0700000000"
        assert "Paybill" in c.get("/estate", headers=T).json()["payment_info"]

        # lease: terms, end date, agreement file
        r = c.patch(f"/leases/{tid}", headers=L, json={"monthly_rent": 7000, "deposit": 7000, "rent_due_day": 28,
                    "lease_end": "2027-09-30", "lease_terms": "12 months. One month notice."})
        assert r.json()["monthly_rent"] == 7000 and r.json()["lease_end"] == "2027-09-30"
        assert c.patch(f"/leases/{tid}", headers=L, json={"monthly_rent": 1, "deposit": 0, "rent_due_day": 28, "lease_end": "2020-01-01"}).status_code == 422
        assert c.get(f"/leases/{tid}/file", headers=T).status_code == 404
        assert c.post(f"/leases/{tid}/file", headers=T, files={"file": ("a.pdf", b"%PDF-1", "application/pdf")}).status_code == 403
        assert c.post(f"/leases/{tid}/file", headers=L, files={"file": ("a.exe", b"MZ", "application/octet-stream")}).status_code == 422
        r = c.post(f"/leases/{tid}/file", headers=L, files={"file": ("Lease Lucy.pdf", b"%PDF-1.4 lease", "application/pdf")})
        assert r.json()["has_lease_file"] and r.json()["lease_file_name"] == "Lease Lucy.pdf"
        got = c.get(f"/leases/{tid}/file", headers=T)
        assert got.status_code == 200 and got.content == b"%PDF-1.4 lease" and got.headers["content-type"] == "application/pdf"

        # bills: reading creates a water bill; rent run is idempotent; manual bill
        assert c.get("/bills", headers=T).json() == []
        rd = c.post("/readings", headers=L, data={"tenancy_id": tid, "value": 58}).json()
        bills = c.get("/bills", headers=T).json()
        assert len(bills) == 1 and bills[0]["kind"] == "WATER" and bills[0]["amount"] == 8 * 150 and not bills[0]["overdue"]
        assert c.post("/bills/generate-rent", headers=T).status_code == 403
        assert c.post("/bills/generate-rent", headers=L).json()["created"] >= 1
        assert c.post("/bills/generate-rent", headers=L).json()["created"] == 0
        r = c.post("/bills", headers=L, json={"tenancy_id": tid, "kind": "OTHER", "description": "Broken key", "amount": 300, "due_date": "2020-01-01"})
        assert r.status_code == 201 and r.json()["overdue"] is True
        other_id = r.json()["id"]

        dash = c.get("/dashboard", headers=T).json()
        assert dash["pending_total"] == 1200 + 7000 + 300
        from datetime import date
        assert dash["overdue_total"] == 300 + (7000 if date.today().day > 28 else 0)  # rent falls due on the 28th
        assert dash["tenant"]["unit_number"] == "D1" and dash["estate"]["payment_info"].startswith("M-Pesa")
        assert dash["unread_notifications"] >= 3 and len(dash["pending_bills"]) == 3
        assert c.get("/dashboard", headers=L).status_code == 403

        # paying, deleting, and the reading/bill link
        water_id = bills[0]["id"]
        assert c.patch(f"/bills/{water_id}/status", headers=T, json={"status": "PAID"}).status_code == 403
        r = c.patch(f"/bills/{water_id}/status", headers=L, json={"status": "PAID"})
        assert r.json()["status"] == "PAID" and r.json()["paid_at"]
        assert c.delete(f"/bills/{water_id}", headers=L).status_code == 409
        assert c.delete(f"/readings/{rd['id']}", headers=L).status_code == 409      # its bill is paid
        assert c.delete(f"/bills/{other_id}", headers=L).status_code == 204
        paid = c.get("/bills?status=PAID", headers=T).json()
        assert [b["id"] for b in paid] == [water_id]
        ov = c.get("/overview", headers=L).json()
        assert ov["pending_bills_total"] >= 7000

        # inbox
        inbox = c.get("/notifications", headers=T).json()
        assert inbox["unread"] >= 3 and any(n["kind"] == "bill" for n in inbox["items"])
        landlord_before = c.get("/notifications", headers=L).json()["unread"]
        assert c.post("/notifications/read", headers=T).status_code == 204
        assert c.get("/notifications", headers=T).json()["unread"] == 0
        assert c.get("/notifications", headers=L).json()["unread"] == landlord_before  # inboxes are separate


def test_payment_approval_flow_and_reading_dates():
    with TestClient(app) as c:
        L = auth(c.post("/auth/login", json={"phone": "0700000000", "password": "changeme123"}).json()["access_token"])
        tid = c.post("/tenants", headers=L, json={"name": "Esther Kavata", "phone": "0755000111", "unit_number": "E1",
                     "move_in_date": "2026-10-01", "initial_meter_reading": 0, "password": "secret12",
                     "monthly_rent": 5000}).json()["tenant"]["tenancy_id"]
        T = auth(c.post("/auth/login", json={"phone": "0755000111", "password": "secret12"}).json()["access_token"])
        X = auth(c.post("/auth/login", json={"phone": "0722111222", "password": "newpass1"}).json()["access_token"])  # another tenant

        bill = c.post("/bills", headers=L, json={"tenancy_id": tid, "kind": "RENT", "description": "Rent, December",
                      "amount": 5000, "due_date": "2020-01-01"}).json()
        bid = bill["id"]
        assert bill["status"] == "PENDING" and bill["overdue"] is True

        # tenant says they paid
        assert c.post(f"/bills/{bid}/submit-payment", headers=T, json={"reference": "ab"}).status_code == 422
        assert c.post(f"/bills/{bid}/submit-payment", headers=X, json={"reference": "SGH7K2LMQ9"}).status_code == 404  # not their bill
        assert c.post(f"/bills/{bid}/submit-payment", headers=L, json={"reference": "SGH7K2LMQ9"}).status_code == 404  # landlord can't
        r = c.post(f"/bills/{bid}/submit-payment", headers=T, json={"reference": " sgh7k2lmq9 "})
        assert r.status_code == 200 and r.json()["status"] == "SUBMITTED" and r.json()["payment_reference"] == "SGH7K2LMQ9"
        assert r.json()["overdue"] is False        # waiting on the landlord, not late
        assert c.post(f"/bills/{bid}/submit-payment", headers=T, json={"reference": "SGH7K2LMQ9"}).status_code == 409

        # landlord sees it waiting; tenant sees it in the dashboard as awaiting
        assert c.get("/overview", headers=L).json()["awaiting_approval"] == 1
        dash = c.get("/dashboard", headers=T).json()
        assert dash["awaiting_total"] == 5000 and dash["pending_total"] == 0
        assert any(n["title"].startswith("Payment to approve") for n in c.get("/notifications", headers=L).json()["items"])
        assert c.delete(f"/bills/{bid}", headers=L).status_code == 409       # must be reviewed first
        assert c.patch(f"/bills/{bid}/status", headers=L, json={"status": "SUBMITTED"}).status_code == 422
        assert c.post(f"/bills/{bid}/review", headers=T, json={"approve": True}).status_code == 403

        # landlord rejects: back to pending with a reason the tenant can read
        r = c.post(f"/bills/{bid}/review", headers=L, json={"approve": False, "note": "Amount was short"})
        j = r.json()
        assert j["status"] == "PENDING" and "Amount was short" in j["review_note"] and j["payment_reference"] is None
        mine = c.get("/bills", headers=T).json()[0]
        assert mine["status"] == "PENDING" and "SGH7K2LMQ9" in mine["review_note"]
        assert c.post(f"/bills/{bid}/review", headers=L, json={"approve": True}).status_code == 409  # nothing to review

        # tenant tries again, landlord approves: tenant sees PAID
        c.post(f"/bills/{bid}/submit-payment", headers=T, json={"reference": "SGH7K2LMQ9"})
        r = c.post(f"/bills/{bid}/review", headers=L, json={"approve": True})
        assert r.json()["status"] == "PAID" and r.json()["paid_at"] and r.json()["review_note"] is None
        assert c.get("/bills", headers=T).json()[0]["status"] == "PAID"
        assert c.get("/dashboard", headers=T).json()["pending_total"] == 0
        assert any(n["title"] == "Payment approved" for n in c.get("/notifications", headers=T).json()["items"])
        assert c.get("/overview", headers=L).json()["awaiting_approval"] == 0

        # marking unpaid wipes the old claim
        r = c.patch(f"/bills/{bid}/status", headers=L, json={"status": "PENDING"})
        assert r.json()["status"] == "PENDING" and r.json()["paid_at"] is None

        # a reading dated before the previous reading is refused; an earlier date is fine for the first
        ok = c.post("/readings", headers=L, data={"tenancy_id": tid, "value": 3, "recorded_at": "2026-11-02T10:00:00Z"})
        assert ok.status_code == 201, ok.text
        bad = c.post("/readings", headers=L, data={"tenancy_id": tid, "value": 6, "recorded_at": "2026-11-01T10:00:00Z"})
        assert bad.status_code == 422 and "before the last reading" in bad.json()["detail"]
        assert c.post("/readings", headers=L, data={"tenancy_id": tid, "value": 6, "recorded_at": "2026-11-03T10:00:00Z"}).status_code == 201


def test_reading_scan_response_serializes_service_dataclass():
    from app.routers.readings import ReadingScanOut
    from app.services.meter_reader import ReadingSuggestion

    suggestion = ReadingSuggestion(
        value=595.825,
        confidence=0.91,
        per_digit_confidence=[0.9, 0.95, 0.92, 0.9, 0.93, 0.88, 0.91, 0.89],
        digits="00595825",
    )
    response = ReadingScanOut.model_validate(suggestion)
    assert response.value == 595.825
    assert response.digits == "00595825"
    assert len(response.per_digit_confidence) == 8
