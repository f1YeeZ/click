import base64
import json
import os
import re
import socket
import time
import unittest
from urllib.parse import parse_qs, unquote, urlparse

from playwright.sync_api import expect, sync_playwright


BASE_URL = os.environ.get("E2E_BASE_URL", "http://localhost:5173")

TEST_PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVR42mP8z8AARAwMjDAGAA8BAQDJoy0AAAAASUVORK5CYII="
)

MICE = [
    {
        "id": "mouse-a",
        "brand": "Razer",
        "model": "Viper V3 Pro",
        "variant": "",
        "displayName": "Razer Viper V3 Pro",
        "shapeType": "SYMMETRICAL",
        "connectionModes": ["wireless_2_4g", "wired"],
        "weightG": 54,
        "sensorName": "Focus Pro 35K Gen-2",
        "maxPollingRateHz": 8000,
        "lengthMm": 127.1,
        "widthMm": 63.9,
        "heightMm": 39.9,
    },
    {
        "id": "mouse-b",
        "brand": "Logitech",
        "model": "G Pro X Superlight 2",
        "variant": "",
        "displayName": "Logitech G Pro X Superlight 2",
        "shapeType": "SYMMETRICAL",
        "connectionModes": ["wireless_2_4g", "wired"],
        "weightG": 60,
        "sensorName": "HERO 2",
        "maxPollingRateHz": 4000,
        "lengthMm": 125,
        "widthMm": 63.5,
        "heightMm": 40,
    },
]


def wait_for_vite(timeout_seconds=30):
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        try:
            with socket.create_connection((urlparse(BASE_URL).hostname, 5173), timeout=0.5):
                return
        except OSError:
            time.sleep(0.2)
    raise RuntimeError("Vite did not start on port 5173 within 30 seconds")


class ClickerSmokeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        wait_for_vite()
        cls.playwright = sync_playwright().start()
        cls.browser = cls.playwright.chromium.launch(headless=True, channel="chromium")

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.playwright.stop()

    def setUp(self):
        self.page = self.browser.new_page()
        self.requests = []
        self.mice = list(MICE)
        self.detail_mouse_image_url = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVR42mP8z8AARAwMjDAGAA8BAQDJoy0AAAAASUVORK5CYII="
        self.review_saved = False
        self.public_review_count = 1
        self.personal_support_dabs_by_grip = {
            "PALM": [], "CLAW": [], "FINGERTIP": [], "MIXED": [],
        }
        self.support_summary = {
            "sampleCount": 0, "positions": [], "cells": [], "maxCount": 0,
            "gridColumns": 64, "gridRows": 96,
        }
        self.dashboard_mice_total = 4
        self.import_committed = False
        self.allow_user_refresh = False
        self.allow_admin_refresh = False
        self.public_config = {
            "maintenanceNotice": "", "registrationEnabled": True, "reviewSubmissionEnabled": True,
            "advertisingEnabled": False,
            "leftAd": {"enabled": True, "imageUrl": "", "targetUrl": "", "altText": ""},
            "rightAd": {"enabled": True, "imageUrl": "", "targetUrl": "", "altText": ""},
        }
        self.settings = [
            {"key": "registration.enabled", "value": "true", "description": "是否允许注册", "updatedBy": "system"},
            {"key": "reviews.enabled", "value": "true", "description": "是否允许用户提交或修改支撑位置记录", "updatedBy": "system"},
        ]
        self.managed_user = {
            "id": "managed-user", "email": "managed@example.com", "role": "USER", "status": "ACTIVE",
            "handSize": "MEDIUM", "handLengthCm": 18.0, "preferredGripStyle": "CLAW",
            "statusReason": None, "statusChangedBy": None, "statusChangedAt": None,
            "createdAt": "2026-07-20T10:00:00+08:00", "updatedAt": "2026-07-20T10:00:00+08:00",
        }
        self.review_report = {
            "id": "report-review-a", "targetType": "REVIEW", "targetLabel": "Razer Viper V3 Pro 的支撑记录",
            "category": "SUSPICIOUS", "description": "该支撑记录的涂抹范围疑似异常。",
            "reporterEmail": "review-reporter@example.com", "status": "OPEN", "assigneeEmail": None,
            "resolution": None, "createdAt": "2026-07-27T11:00:00+08:00",
        }
        self.admin_review = {
            "id": "review-admin-a", "userId": "reviewer-a", "mouseId": "mouse-a",
            "userEmail": "reviewer@example.com", "mouseName": "Razer Viper V3 Pro",
            "status": "ACTIVE", "handSize": "MEDIUM",
            "supportPositions": ["PALM_CENTER"], "supportCells": [],
            "supportDabs": [
                {"x": 460, "y": 560, "radius": 95, "mode": "PAINT"},
                {"x": 540, "y": 570, "radius": 80, "mode": "PAINT"},
            ],
            "supportByGrip": [
                {"gripStyle": "CLAW", "supportDabs": [{"x": 460, "y": 560, "radius": 95, "mode": "PAINT"}], "supportCells": []},
                {"gripStyle": "PALM", "supportDabs": [{"x": 540, "y": 570, "radius": 80, "mode": "PAINT"}], "supportCells": []},
            ],
            "moderationReason": None, "moderatedBy": None, "moderatedAt": None,
            "createdAt": "2026-07-26T17:30:00+08:00",
            "supportMarkCount": 2,
            "reportCount": 1, "openReportCount": 1, "riskLevel": "MEDIUM", "riskFlags": ["有举报"],
            "reports": [self.review_report],
        }
        self.admin_reviews = [
            self.admin_review,
            {
                "id": "review-admin-pending", "userId": "reviewer-b", "mouseId": "mouse-a",
                "userEmail": "pending@example.com", "mouseName": "Razer Viper V3 Pro",
                "status": "PENDING", "handSize": "SMALL", "supportPositions": [], "supportCells": [],
                "supportDabs": [], "supportByGrip": [], "supportMarkCount": 0,
                "moderationReason": None, "moderatedBy": None, "moderatedAt": None,
                "createdAt": "2026-07-27T09:10:00+08:00",
                "reportCount": 0, "openReportCount": 0, "riskLevel": "HIGH", "riskFlags": ["内容不完整"],
                "reports": [],
            },
            {
                "id": "review-admin-disabled", "userId": "reviewer-c", "mouseId": "mouse-a",
                "userEmail": "disabled@example.com", "mouseName": "Razer Viper V3 Pro",
                "status": "DISABLED", "handSize": "LARGE", "supportPositions": [], "supportCells": [],
                "supportDabs": [], "supportByGrip": [{"gripStyle": "PALM", "supportDabs": [], "supportCells": []}],
                "supportMarkCount": 1, "moderationReason": "异常范围", "moderatedBy": "admin@example.com",
                "moderatedAt": "2026-07-27T10:00:00+08:00", "createdAt": "2026-07-25T12:00:00+08:00",
                "reportCount": 0, "openReportCount": 0, "riskLevel": "LOW", "riskFlags": [], "reports": [],
            },
            {
                "id": "review-admin-mouse-b", "userId": "reviewer-d", "mouseId": "mouse-b",
                "userEmail": "logitech@example.com", "mouseName": "Logitech G Pro X Superlight 2",
                "status": "ACTIVE", "handSize": "MEDIUM", "supportPositions": [], "supportCells": [],
                "supportDabs": [], "supportByGrip": [{"gripStyle": "CLAW", "supportDabs": [], "supportCells": []}],
                "supportMarkCount": 1, "moderationReason": None, "moderatedBy": None, "moderatedAt": None,
                "createdAt": "2026-07-24T08:00:00+08:00", "reportCount": 0, "openReportCount": 0,
                "riskLevel": "LOW", "riskFlags": [], "reports": [],
            },
        ]
        self.admin_report = {
            "id": "report-a", "targetType": "MOUSE", "targetLabel": "Razer Viper V3 Pro",
            "category": "参数纠错", "description": "重量参数可能不准确，请复核官方页面。",
            "reporterEmail": "reporter@example.com", "status": "OPEN", "assigneeEmail": None,
            "resolution": None, "createdAt": "2026-07-27T10:00:00+08:00",
        }
        self.page.add_init_script("window.EventSource = undefined")
        self.page.route("https://fonts.googleapis.com/**", lambda route: route.fulfill(content_type="text/css", body=""))
        self.page.route("https://fonts.gstatic.com/**", lambda route: route.abort())
        self.page.route("**/api/v1/**", self._mock_api)

    def tearDown(self):
        self.page.close()

    def _mock_api(self, route):
        path = urlparse(route.request.url).path
        method = route.request.method
        try:
            request_payload = route.request.post_data or ""
        except UnicodeDecodeError:
            request_payload = f"<binary:{len(route.request.post_data_buffer or b'')}>"
        self.requests.append((method, path, request_payload))
        if path == "/api/v1/registration-verification-codes" and method == "POST":
            route.fulfill(
                status=201,
                content_type="application/json",
                body=json.dumps({"message": "验证码已发送", "expiresInSeconds": 60, "resendAfterSeconds": 60}, ensure_ascii=False),
            )
            return
        if path == "/api/v1/users" and method == "POST":
            route.fulfill(
                status=201,
                content_type="application/json",
                body=json.dumps({
                    "token": "user-token",
                    "user": {"id": "user-a", "email": "new-member@example.com", "role": "USER"},
                }),
            )
            return
        if path == "/api/v1/sessions" and method == "POST":
            payload = json.loads(route.request.post_data or "{}")
            route.fulfill(content_type="application/json", body=json.dumps({
                "token": "user-token",
                "user": {
                    "id": "admin-a" if payload.get("email") == "admin@example.com" else "user-a",
                    "email": payload.get("email"),
                    "role": "USER",
                },
            }))
            return
        if path == "/api/v1/admin-sessions" and method == "POST":
            route.fulfill(status=202, content_type="application/json", body=json.dumps({
                "secondFactorRequired": True,
                "challengeId": "11111111-1111-1111-1111-111111111111",
                "expiresInSeconds": 60,
            }))
            return
        if path == "/api/v1/admin-sessions/verify" and method == "POST":
            route.fulfill(status=201, content_type="application/json", body=json.dumps({
                "token": "admin-token",
                "user": {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"},
            }))
            return
        if path == "/api/v1/config" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps(self.public_config))
            return
        if path == "/api/v1/sessions/refresh" and method == "POST":
            if not self.allow_user_refresh:
                route.fulfill(status=401, content_type="application/json", body='{"error":{"message":"会话不存在"}}')
                return
            route.fulfill(content_type="application/json", body=json.dumps({
                "token": "user-token", "user": {"id": "user-a", "email": "reviewer@example.com", "role": "USER",
                "handSize": "MEDIUM", "handLengthCm": 18.2, "preferredGripStyle": "CLAW"},
            }))
            return
        if path == "/api/v1/admin-sessions/refresh" and method == "POST":
            if not self.allow_admin_refresh:
                route.fulfill(status=401, content_type="application/json", body='{"error":{"message":"会话不存在"}}')
                return
            route.fulfill(content_type="application/json", body=json.dumps({
                "token": "admin-token", "user": {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"},
            }))
            return
        if path.startswith("/api/v1/admin/") and route.request.headers.get("authorization") != "Bearer admin-token":
            route.fulfill(
                status=401,
                content_type="application/json",
                body=json.dumps({"error": {"message": "请先登录"}}, ensure_ascii=False),
            )
            return
        if path == "/api/v1/users/me" and method == "GET":
            authorization = route.request.headers.get("authorization", "")
            role = "ADMIN" if authorization == "Bearer admin-token" else "USER"
            email = "admin@example.com" if role == "ADMIN" else "reviewer@example.com"
            route.fulfill(content_type="application/json", body=json.dumps({
                "id": "user-a", "email": email, "role": role,
                "handSize": "MEDIUM", "handLengthCm": 18.2, "preferredGripStyle": "CLAW",
            }))
            return
        if path == "/api/v1/review-options" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({
                "gripStyles": [
                    {"code": "PALM", "label": "趴握"}, {"code": "CLAW", "label": "抓握"},
                    {"code": "FINGERTIP", "label": "指握"}, {"code": "MIXED", "label": "混合"},
                ],
                "handSizes": [{"code": "MEDIUM", "label": "中手"}],
                "proTags": [], "conTags": [],
            }, ensure_ascii=False))
            return
        if path == "/api/v1/mice/mouse-a" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({
                "mouse": {**MICE[0], "imageUrl": self.detail_mouse_image_url},
            }))
            return
        if re.fullmatch(r"/api/v1/mice/mouse-[ab]/support-summary", path) and method == "GET":
            summary = dict(self.support_summary)
            if path.startswith("/api/v1/mice/mouse-b/") and summary.get("cells"):
                summary["cells"] = [{**cell, "x": min(63, cell["x"] + 8)} for cell in summary["cells"]]
            route.fulfill(content_type="application/json", body=json.dumps(summary))
            return
        if path == "/api/v1/mice/mouse-a/reviews/mine" and method == "GET":
            if not self.review_saved:
                route.fulfill(status=404, content_type="application/json", body='{"error":{"code":"REVIEW_NOT_FOUND","message":"尚未提交评价"}}')
                return
            route.fulfill(content_type="application/json", body=json.dumps({
                "id": "review-a", "mouseId": "mouse-a", "supportPositions": [], "supportCells": [],
                "supportByGrip": [
                    {"gripStyle": grip_style, "supportDabs": dabs, "supportCells": []}
                    for grip_style, dabs in self.personal_support_dabs_by_grip.items()
                    if dabs
                ],
            }))
            return
        if path == "/api/v1/mice/mouse-a/reviews" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({
                "items": [{"id": f"public-review-{index}", "author": f"user{index}***@example.com", "gripStyle": "CLAW",
                    "handSize": "MEDIUM", "supportByGrip": [{"gripStyle": "CLAW", "supportDabs": [{"x": 500, "y": 650, "radius": 70, "mode": "PAINT"}], "supportCells": []}],
                    "createdAt": "2026-07-26T17:30:00+08:00"} for index in range(self.public_review_count)],
                "page": {"number": 1, "totalPages": 1, "totalItems": self.public_review_count},
            }))
            return
        if path.startswith("/api/v1/mice/mouse-a/reviews/mine/support-positions/") and method == "PUT":
            grip_style = path.rsplit("/", 1)[-1]
            self.personal_support_dabs_by_grip[grip_style] = json.loads(
                route.request.post_data or "{}"
            ).get("dabs", [])
            self.review_saved = True
            route.fulfill(content_type="application/json", body='{}')
            return
        if path == "/api/v1/admin/dashboard" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({
                "miceTotal": self.dashboard_mice_total, "micePublished": 0, "miceDraft": 0, "miceArchived": 0,
                "usersTotal": 0, "usersActive": 0, "reviewsTotal": 0, "reviewsActive": 0,
                "reviewsPending": 0, "dataQualityPercent": 100,
                "miceIncomplete": 0, "miceVerificationStale": 0,
                "usersAdmin": 1, "usersDisabled": 0,
                "recentUsers": [], "recentReviews": [], "recentMice": [],
            }))
            return
        if path == "/api/v1/admin/brands" and method == "GET":
            route.fulfill(content_type="application/json", body="[]")
            return
        if path == "/api/v1/admin/reviews" and method == "GET":
            query = parse_qs(urlparse(route.request.url).query)
            status = query.get("status", [""])[0]
            term = query.get("q", [""])[0].lower()
            items = [review for review in self.admin_reviews if not status or review["status"] == status]
            if term:
                items = [review for review in items if term in review["userEmail"].lower() or term in review["mouseName"].lower()]
            route.fulfill(content_type="application/json", body=json.dumps({
                "items": items, "page": {"number": 1, "totalPages": 1, "totalItems": len(items)},
            }))
            return
        if path == "/api/v1/admin/reviews/review-admin-a" and method == "PATCH":
            payload = json.loads(route.request.post_data or "{}")
            self.admin_review["status"] = payload["status"]
            self.admin_review["moderationReason"] = payload.get("reason")
            self.admin_review["moderatedBy"] = "admin@example.com"
            route.fulfill(content_type="application/json", body=json.dumps(self.admin_review))
            return
        if path == "/api/v1/admin/audit-logs" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({
                "items": [{
                    "id": "audit-a", "actorEmail": "admin@example.com", "action": "MOUSE_UPDATE",
                    "entityType": "MOUSE", "entityId": "mouse-a", "summary": "更新鼠标参数",
                    "beforeState": '{"weightG":55}', "afterState": '{"weightG":54}',
                    "reason": "按官网参数复核", "createdAt": "2026-07-28T12:00:00+08:00",
                }],
                "page": {"number": 1, "totalPages": 1, "totalItems": 1},
            }, ensure_ascii=False))
            return
        if path == "/api/v1/admin/images" and method == "GET":
            route.fulfill(content_type="application/json", body="[]")
            return
        if path == "/api/v1/admin/images" and method == "POST":
            route.fulfill(
                status=201,
                content_type="application/json",
                body=json.dumps({
                    "name": "edited.webp", "url": "/api/v1/images/edited.webp", "size": 1024,
                    "updatedAt": "2026-08-04T12:00:00+08:00",
                }),
            )
            return
        if path == "/api/v1/images/edited.webp" and method == "GET":
            route.fulfill(content_type="image/png", body=TEST_PNG)
            return
        if path == "/api/v1/admin/mice" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({
                "items": [], "page": {"number": 1, "totalPages": 1, "totalItems": 0},
            }))
            return
        if path == "/api/v1/admin/users" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({
                "items": [self.managed_user], "page": {"number": 1, "totalPages": 1, "totalItems": 1},
            }))
            return
        if path == "/api/v1/admin/users/managed-user/detail" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({
                "user": self.managed_user, "reviewCount": 2, "activeSessionCount": 1,
                "sessions": [{"id": "session-a", "active": True, "lastUsedAt": "2026-07-26T18:00:00+08:00"}],
                "recentAudit": [],
            }))
            return
        if path == "/api/v1/admin/users/managed-user/role" and method == "PATCH":
            payload = json.loads(route.request.post_data or "{}")
            self.managed_user["role"] = payload["role"]
            self.managed_user["updatedAt"] = "2026-07-26T18:00:00+08:00"
            route.fulfill(content_type="application/json", body=json.dumps(self.managed_user))
            return
        if path == "/api/v1/admin/users/managed-user" and method == "PATCH":
            payload = json.loads(route.request.post_data or "{}")
            self.managed_user["status"] = payload["status"]
            self.managed_user["statusReason"] = payload.get("reason")
            self.managed_user["statusChangedBy"] = "admin@example.com"
            self.managed_user["statusChangedAt"] = "2026-07-26T18:10:00+08:00"
            route.fulfill(content_type="application/json", body=json.dumps(self.managed_user))
            return
        if path == "/api/v1/admin/mice/imports/preview" and method == "POST":
            route.fulfill(content_type="application/json", body=json.dumps({
                "filename": "mice.csv", "checksum": "checksum-a", "totalRows": 1,
                "validRows": 1, "createRows": 1, "updateRows": 0, "errors": [], "ready": True,
            }))
            return
        if path == "/api/v1/admin/mice/imports" and method == "POST":
            self.import_committed = True
            route.fulfill(content_type="application/json", body=json.dumps({
                "createdCount": 1, "updatedCount": 0, "alreadyImported": False,
            }))
            return
        if path == "/api/v1/admin/analytics" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({
                "days": 30, "points": [{"date": "2026-07-26", "users": 1, "mice": 1, "reviews": 2, "adminActions": 3}],
                "openReports": 1, "unreadNotifications": 1, "activeSessions": 2, "staleMice": 1,
            }))
            return
        if path == "/api/v1/admin/notifications" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({"items": [], "page": {"number": 1, "totalPages": 1, "totalItems": 0}}))
            return
        if path == "/api/v1/admin/brand-profiles" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps([{"id": "brand-a", "name": "Logitech", "status": "ACTIVE", "mouseCount": 2}]))
            return
        if path == "/api/v1/admin/reports" and method == "GET":
            target_types = parse_qs(urlparse(route.request.url).query).get("targetType", [""])[0].split(",")
            items = [self.admin_report, self.review_report]
            if any(target_types):
                items = [item for item in items if item["targetType"] in target_types]
            route.fulfill(content_type="application/json", body=json.dumps({"items": items, "page": {"number": 1, "totalPages": 1, "totalItems": len(items)}}))
            return
        if path == "/api/v1/admin/reports/report-a" and method == "PATCH":
            self.admin_report.update(json.loads(route.request.post_data or "{}"))
            route.fulfill(content_type="application/json", body=json.dumps(self.admin_report))
            return
        if path == "/api/v1/admin/reports/report-review-a" and method == "PATCH":
            self.review_report.update(json.loads(route.request.post_data or "{}"))
            self.admin_review["openReportCount"] = 0
            route.fulfill(content_type="application/json", body=json.dumps(self.review_report))
            return
        if path == "/api/v1/admin/mice/imports" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({"items": [], "page": {"number": 1, "totalPages": 1, "totalItems": 0}}))
            return
        if path == "/api/v1/admin/sessions" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({"items": [], "page": {"number": 1, "totalPages": 1, "totalItems": 0}}))
            return
        if path == "/api/v1/admin/settings" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps(self.settings, ensure_ascii=False))
            return
        if path.startswith("/api/v1/admin/settings/") and method == "PUT":
            key = unquote(path.split("/settings/", 1)[1])
            value = str(json.loads(route.request.post_data or "{}").get("value", ""))
            item = next((setting for setting in self.settings if setting["key"] == key), None)
            if item is None:
                item = {"key": key, "value": value, "description": "", "updatedBy": "admin@example.com"}
                self.settings.append(item)
            else:
                item["value"] = value
                item["updatedBy"] = "admin@example.com"
            if key == "reviews.enabled":
                self.public_config["reviewSubmissionEnabled"] = value.lower() == "true"
            route.fulfill(content_type="application/json", body=json.dumps(item, ensure_ascii=False))
            return
        if path == "/api/v1/mice/brands":
            route.fulfill(content_type="application/json", body=json.dumps(["Logitech", "Razer"]))
            return
        if path == "/api/v1/mice":
            query = parse_qs(urlparse(route.request.url).query).get("q", [""])[0].lower()
            items = [mouse for mouse in self.mice if not query or query in mouse["model"].lower()]
            body = {"items": items, "page": {"number": 1, "totalPages": 1 if items else 0, "totalItems": len(items)}}
            route.fulfill(content_type="application/json", body=json.dumps(body))
            return
        if path == "/api/v1/mouse-comparisons":
            body = {
                "items": MICE,
                "rows": [
                    {
                        "group": "尺寸与重量",
                        "label": "重量",
                        "unit": "g",
                        "different": True,
                        "cells": [{"value": "54", "delta": None}, {"value": "60", "delta": "+11.1%"}],
                    }
                ],
            }
            route.fulfill(content_type="application/json", body=json.dumps(body))
            return
        if path == "/api/v1/password-reset-verification-codes":
            route.fulfill(
                status=201,
                content_type="application/json",
                body=json.dumps({
                    "message": "如果该邮箱已注册，重置验证码将发送至邮箱",
                    "expiresInSeconds": 60,
                    "resendAfterSeconds": 60,
                }, ensure_ascii=False),
            )
            return
        if path == "/api/v1/password-reset":
            route.fulfill(
                content_type="application/json",
                body=json.dumps({"message": "密码重置成功，请使用新密码登录"}, ensure_ascii=False),
            )
            return
        if path == "/api/v1/feedback" and method == "POST":
            route.fulfill(status=201, content_type="application/json", body=json.dumps({"message": "反馈已提交"}, ensure_ascii=False))
            return
        if path == "/api/v1/mouse-recommendations" and method == "POST":
            exact = {
                "rank": 1, "mouse": MICE[0], "exactMatchCount": 3, "eligibleReviewCount": 4,
                "positionEvidence": {"PALM_CENTER": 3, "PALM_HEEL": 3}, "lowSample": True,
                "matchType": "EXACT", "supportCoveragePercent": 92, "shapeSimilarityPercent": 78,
                "matchedSupportSampleCount": 3,
                "explanation": "3 份同握姿评价达到图形匹配标准：期望范围覆盖 92%、形状相似度 78%。",
            }
            near = {
                "rank": 2, "mouse": MICE[1], "exactMatchCount": 0, "eligibleReviewCount": 5,
                "positionEvidence": {"PALM_CENTER": 2, "PALM_HEEL": 0}, "lowSample": True,
                "matchType": "NEAR", "supportCoveragePercent": 100, "shapeSimilarityPercent": 38,
                "matchedSupportSampleCount": 2,
                "explanation": "相近匹配：最佳单份同握姿评价覆盖期望范围 100%、形状相似度 38%；未同时达到图形匹配标准。",
            }
            route.fulfill(content_type="application/json", body=json.dumps({
                "gripStyle": "CLAW", "requestedPositions": ["PALM_CENTER", "PALM_HEEL"],
                "evaluatedMouseCount": 2, "items": [exact, near],
            }, ensure_ascii=False))
            return
        route.fulfill(status=404, content_type="application/json", body='{"error":{"message":"not mocked"}}')

    def test_catalog_filter_is_reflected_in_shareable_url(self):
        self.page.goto(f"{BASE_URL}/mice")
        self.page.wait_for_load_state("networkidle")
        self.page.get_by_label("型号", exact=True).fill("Viper")
        self.page.wait_for_url("**/mice?q=Viper")
        expect(self.page.locator("article.mouse-card")).to_have_count(1)
        expect(self.page.get_by_text("Viper V3 Pro", exact=True)).to_be_visible()

    def test_catalog_has_no_rating_sort_or_score_cards(self):
        self.page.goto(f"{BASE_URL}/mice")
        self.page.wait_for_load_state("networkidle")
        expect(self.page.get_by_label("排序方式").locator('option[value="rating_desc"]')).to_have_count(0)
        expect(self.page.locator("article.mouse-card .card-rating")).to_have_count(0)

    def test_catalog_filter_uses_the_geardb_query_workbench(self):
        self.mice = [
            {**MICE[index % len(MICE)], "id": f"filter-mouse-{index}"}
            for index in range(12)
        ]
        self.page.set_viewport_size({"width": 1440, "height": 700})
        self.page.goto(f"{BASE_URL}/mice")
        self.page.wait_for_load_state("networkidle")

        active_nav = self.page.get_by_role("navigation", name="主导航").get_by_role("link", name="鼠标库", exact=True)
        expect(active_nav).to_have_class(re.compile("router-link-active"))
        active_nav_style = active_nav.evaluate("""element => ({
            backgroundColor: getComputedStyle(element).backgroundColor,
            borderRadius: getComputedStyle(element).borderRadius,
        })""")
        self.assertNotEqual(active_nav_style["backgroundColor"], "rgba(0, 0, 0, 0)")
        self.assertEqual(active_nav_style["borderRadius"], "11px")

        workbench = self.page.locator(".filter-workbench")
        expect(workbench).to_be_visible()
        self.assertLess(workbench.bounding_box()["height"], 90)
        expect(self.page.locator(".filter-domain-tabs button")).to_have_count(0)
        expect(self.page.locator(".filter-accordion-toggle")).to_have_count(0)

        self.page.evaluate("document.documentElement.style.scrollBehavior = 'auto'; window.scrollTo(0, 600)")
        self.assertGreater(self.page.evaluate("window.scrollY"), 500)
        sticky_top = self.page.locator(".database-filter-rail").bounding_box()["y"]
        self.assertAlmostEqual(sticky_top, 72, delta=2)

        screenshot_path = os.environ.get("E2E_FILTER_WORKBENCH_SCREENSHOT")
        if screenshot_path:
            workbench.screenshot(path=screenshot_path)

        lightweight = self.page.get_by_role("button", name="≤ 60g")
        lightweight.click()
        expect(lightweight).to_have_attribute("aria-pressed", "true")
        self.page.wait_for_url("**weightMax=60**")

        results_top_before = self.page.locator(".database-results").evaluate("element => element.offsetTop")
        self.page.get_by_role("button", name=re.compile("更多筛选")).click()
        expect(self.page.locator(".filter-domain-tabs button")).to_have_count(5)
        expect(self.page.locator(".filter-advanced-panel")).to_be_visible()
        self.assertEqual(
            self.page.locator(".filter-advanced-panel .filter-check-options").first.evaluate(
                "element => getComputedStyle(element).overflowY"
            ),
            "visible",
        )
        self.assertEqual(
            self.page.locator(".filter-domain-panel").evaluate(
                "element => getComputedStyle(element).gridTemplateColumns.split(' ').length"
            ),
            12,
        )
        advanced_screenshot_path = os.environ.get("E2E_FILTER_ADVANCED_SCREENSHOT")
        if advanced_screenshot_path:
            self.page.locator(".filter-advanced-panel").screenshot(path=advanced_screenshot_path)
        results_top_after = self.page.locator(".database-results").evaluate("element => element.offsetTop")
        self.assertEqual(results_top_after, results_top_before)
        self.page.get_by_role("button", name=re.compile("外形尺寸")).click()
        panel = self.page.locator(".filter-domain-panel")
        expect(panel.get_by_role("slider", name="长度最小值")).to_be_visible()
        expect(panel.locator(".range-slider")).to_have_count(3)
        for removed_label in ["隆起位置", "前端外扩", "侧面曲率", "拇指托", "无名指托"]:
            expect(panel.get_by_text(removed_label, exact=True)).to_have_count(0)

        self.page.get_by_role("button", name=re.compile("传感性能")).click()
        expect(panel.get_by_text("传感器类型", exact=True)).to_have_count(0)
        self.page.get_by_role("button", name=re.compile("按键微动")).click()
        for removed_label in ["总按键数", "侧键数", "微动型号"]:
            expect(panel.get_by_text(removed_label, exact=True)).to_have_count(0)
        self.page.get_by_role("button", name=re.compile("材质渠道")).click()
        for removed_label in ["编码器类型", "编码器型号", "滚轮步数"]:
            expect(panel.get_by_text(removed_label, exact=True)).to_have_count(0)
        self.assertLessEqual(self.page.evaluate("document.documentElement.scrollWidth"), 1440)

    def test_mobile_catalog_keeps_navigation_and_filters_touch_friendly(self):
        self.page.set_viewport_size({"width": 390, "height": 844})
        selected = [{"id": MICE[0]["id"], "displayName": MICE[0]["displayName"]}]
        serialized = json.dumps(selected, ensure_ascii=False)
        self.page.add_init_script(
            f"localStorage.setItem('clicker.compare', {json.dumps(serialized)})"
        )
        self.page.goto(f"{BASE_URL}/mice")
        self.page.wait_for_load_state("networkidle")

        mobile_nav = self.page.get_by_role("navigation", name="移动主导航")
        expect(mobile_nav).to_be_visible()
        expect(mobile_nav.get_by_role("link", name="首页", exact=True)).to_have_count(0)
        expect(mobile_nav.get_by_role("link")).to_have_count(3)
        expect(mobile_nav.locator(".mobile-compare-count")).to_have_text("1")
        expect(self.page.locator(".compare-tray")).to_be_hidden()
        expect(self.page.locator(".feedback-fab")).to_be_hidden()
        expect(self.page.get_by_role("link", name="登录", exact=True)).to_be_visible()
        expect(self.page.locator(".database-filter-rail")).to_be_hidden()

        trigger = self.page.get_by_role("button", name=re.compile("筛选与排序"))
        expect(trigger).to_be_visible()
        trigger.click()
        expect(self.page.locator(".database-filter-rail")).to_be_visible()
        expect(self.page.get_by_role("button", name=re.compile("查看 .* 款结果"))).to_be_visible()
        self.page.get_by_role("button", name=re.compile("更多筛选")).click()
        expect(self.page.locator(".filter-advanced-panel")).to_be_visible()
        self.assertEqual(
            self.page.locator(".filter-domain-panel").evaluate(
                "element => getComputedStyle(element).gridTemplateColumns.split(' ').length"
            ),
            1,
        )
        for selector in [".filter-quick-row", ".filter-domain-tabs"]:
            container = self.page.locator(selector).last
            self.assertLessEqual(
                container.evaluate("element => element.scrollWidth"),
                container.evaluate("element => element.clientWidth"),
            )

        metrics = self.page.evaluate("""() => ({
            viewport: document.documentElement.clientWidth,
            scrollWidth: document.documentElement.scrollWidth,
            navHeights: [...document.querySelectorAll('.mobile-nav a')].map((item) => item.getBoundingClientRect().height),
            inputFontSize: getComputedStyle(document.querySelector('.filter-search input')).fontSize,
        })""")
        self.assertLessEqual(metrics["scrollWidth"], metrics["viewport"])
        self.assertTrue(all(height >= 44 for height in metrics["navHeights"]))
        self.assertEqual(metrics["inputFontSize"], "16px")

    def test_custom_select_keeps_its_styling_and_viewport_anchor(self):
        for viewport in [
            {"width": 320, "height": 568},
            {"width": 768, "height": 1024},
            {"width": 1440, "height": 900},
            {"width": 2560, "height": 1160},
        ]:
            self.page.set_viewport_size(viewport)
            self.page.goto(f"{BASE_URL}/mice")
            self.page.wait_for_load_state("networkidle")
            select = self.page.get_by_label("排序方式")
            select.click()
            popup = self.page.locator(".select-enhancer-popup")
            expect(popup).to_be_visible()
            self.page.wait_for_function(
                "element => getComputedStyle(element).opacity === '1'",
                arg=popup.element_handle(),
            )
            trigger_bounds = select.bounding_box()
            popup_bounds = popup.bounding_box()
            popup_style = popup.evaluate("""element => ({
                position: getComputedStyle(element).position,
                radius: getComputedStyle(element).borderRadius,
                opacity: getComputedStyle(element).opacity,
            })""")
            self.assertEqual(popup_style["position"], "fixed")
            self.assertNotEqual(popup_style["radius"], "0px")
            self.assertEqual(popup_style["opacity"], "1")
            self.assertGreaterEqual(popup_bounds["x"], 7)
            self.assertGreaterEqual(popup_bounds["y"], 7)
            self.assertLessEqual(popup_bounds["x"] + popup_bounds["width"], viewport["width"] - 7)
            self.assertLessEqual(popup_bounds["y"] + popup_bounds["height"], viewport["height"] - 7)
            if trigger_bounds["x"] + popup_bounds["width"] <= viewport["width"] - 8:
                self.assertAlmostEqual(
                    popup_bounds["x"],
                    trigger_bounds["x"],
                    delta=1,
                    msg=(viewport, trigger_bounds, popup_bounds, popup_style),
                )
            self.assertAlmostEqual(popup_bounds["width"], trigger_bounds["width"], delta=1)
            screenshot_path = os.environ.get("E2E_SELECT_SCREENSHOT")
            if screenshot_path and viewport["width"] == 1440:
                self.page.screenshot(path=screenshot_path, full_page=True)
            self.page.keyboard.press("Escape")
            expect(popup).to_have_count(0)

        select.click()
        self.page.locator(".select-enhancer-popup").get_by_role("option", name="品牌 A—Z").click()
        self.assertEqual(select.input_value(), "brand_asc")

        self.page.evaluate("""() => {
            const select = document.createElement('select')
            select.setAttribute('aria-label', '动态测试下拉框')
            select.innerHTML = '<option>测试选项</option>'
            document.body.append(select)
        }""")
        dynamic_select = self.page.locator('select[aria-label="动态测试下拉框"]')
        dynamic_select.click()
        expect(self.page.locator(".select-enhancer-popup")).to_be_visible()
        dynamic_select.evaluate("element => element.remove()")
        expect(self.page.locator(".select-enhancer-popup")).to_have_count(0)

    def test_mobile_admin_navigation_stays_reachable(self):
        admin = {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"}
        self.allow_admin_refresh = True
        self.page.set_viewport_size({"width": 390, "height": 844})
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.admin.token', 'admin-token'); sessionStorage.setItem('clicker.admin.user', {json.dumps(json.dumps(admin))});"
        )
        self.page.goto(f"{BASE_URL}/admin")
        self.page.get_by_role("button", name=re.compile("系统运营")).click()

        expect(self.page.get_by_role("heading", name="系统运营", level=2)).to_be_visible()
        expect(self.page.locator(".expansion-loading")).to_be_hidden()
        nav_metrics = self.page.locator(".admin-sidebar").evaluate("""(sidebar) => ({
            position: getComputedStyle(sidebar).position,
            buttonHeights: [...sidebar.querySelectorAll('nav button')].map((item) => item.getBoundingClientRect().height),
            hoveredButtonTransform: getComputedStyle(sidebar.querySelector('nav button:last-child')).transform,
            navClientWidth: sidebar.querySelector('nav').clientWidth,
            navScrollWidth: sidebar.querySelector('nav').scrollWidth,
        })""")
        self.assertEqual(nav_metrics["position"], "sticky")
        self.assertTrue(all(height >= 44 for height in nav_metrics["buttonHeights"]))
        self.assertEqual(nav_metrics["hoveredButtonTransform"], "none")
        self.assertLessEqual(nav_metrics["navScrollWidth"], nav_metrics["navClientWidth"])
        screenshot_path = os.environ.get("E2E_ADMIN_MOBILE_SCREENSHOT")
        if screenshot_path:
            self.page.screenshot(path=screenshot_path, full_page=False)

    def test_z_detail_focuses_on_support_heatmap_without_scores(self):
        self.public_review_count = 4
        self.page.goto(f"{BASE_URL}/mice/mouse-a")
        self.page.wait_for_load_state("networkidle")
        review_list = self.page.locator(".public-review-rail")
        self.assertEqual(review_list.evaluate("element => getComputedStyle(element).display"), "flex")
        self.assertEqual(review_list.evaluate("element => getComputedStyle(element).overflowX"), "auto")
        self.assertGreater(review_list.evaluate("element => element.scrollWidth"), review_list.evaluate("element => element.clientWidth"))
        expect(self.page.get_by_role("button", name="查看下一条用户评论")).to_be_visible()
        before_scroll = review_list.evaluate("element => element.scrollLeft")
        self.page.get_by_role("button", name="查看下一条用户评论").click()
        self.page.wait_for_timeout(300)
        self.assertGreater(review_list.evaluate("element => element.scrollLeft"), before_scroll)
        self.assertTrue(self.page.locator(".public-review-ticket").evaluate_all(
            "cards => cards.every(card => card.getBoundingClientRect().width <= 320 && card.getBoundingClientRect().height < 330)"
        ))
        expect(self.page.locator(".public-review-ticket .hand-support-3d")).to_have_count(0)
        expect(self.page.locator(".public-review-ticket .hand-support-2d")).to_have_count(4)
        support_preview_ratios = self.page.locator(".public-review-ticket .hand-support-2d canvas").evaluate_all("""canvases => canvases.map((canvas) => {
            const bounds = canvas.getBoundingClientRect()
            return {
                display: bounds.width / bounds.height,
                bitmap: canvas.width / canvas.height,
            }
        })""")
        self.assertTrue(all(
            abs(item["display"] - item["bitmap"]) <= 0.01
            for item in support_preview_ratios
        ), support_preview_ratios)
        expect(self.page.locator(".detail-mouse-viewport .detail-product-image")).to_be_visible()
        expect(self.page.get_by_role("heading", name="鼠标三维模型")).to_have_count(0)
        expect(self.page.locator(".detail-mouse-viewport canvas")).to_have_count(0)
        screenshot_path = os.environ.get("E2E_DETAIL_REVIEW_SCREENSHOT")
        if screenshot_path:
            review_list.evaluate("element => { element.style.scrollBehavior = 'auto'; element.scrollTo(0, 0) }")
            self.page.wait_for_timeout(50)
            self.page.screenshot(path=screenshot_path, full_page=True)
        self.page.set_viewport_size({"width": 390, "height": 844})
        review_list.scroll_into_view_if_needed()
        mobile_metrics = review_list.evaluate("""element => {
            const card = element.querySelector('.public-review-ticket')
            return {
                viewportWidth: document.documentElement.clientWidth,
                pageWidth: document.documentElement.scrollWidth,
                railWidth: element.clientWidth,
                cardWidth: card?.getBoundingClientRect().width || 0,
                snapType: getComputedStyle(element).scrollSnapType,
            }
        }""")
        self.assertLessEqual(mobile_metrics["pageWidth"], mobile_metrics["viewportWidth"])
        self.assertLessEqual(mobile_metrics["cardWidth"], mobile_metrics["railWidth"])
        self.assertIn("mandatory", mobile_metrics["snapType"])
        mobile_screenshot_path = os.environ.get("E2E_DETAIL_REVIEW_MOBILE_SCREENSHOT")
        if mobile_screenshot_path:
            self.page.screenshot(path=mobile_screenshot_path, full_page=False)
        expect(self.page.get_by_role("heading", name="3D 手掌支撑热力图")).to_be_visible()
        expect(self.page.locator(".detail-hand-viewport .heatmap-score")).to_have_count(0)
        expect(self.page.locator(".detail-hand-viewport .heatmap-filter-bar select")).to_have_count(2)
        statline = self.page.locator(".hero-statline")
        detail_stage = self.page.locator(".detail-model-stage")
        self.assertLessEqual(statline.bounding_box()["y"] + statline.bounding_box()["height"], detail_stage.bounding_box()["y"])
        product_panel = self.page.locator(".detail-mouse-viewport")
        panel_size_before = product_panel.bounding_box()
        self.page.get_by_role("tab", name="完整参数").click()
        objective_data = self.page.locator("#product-specs-panel")
        expect(objective_data).to_be_visible()
        expect(self.page.locator("#product-image-panel")).to_have_count(0)
        expect(self.page.locator(".objective-dialog")).to_have_count(0)
        panel_size_after = product_panel.bounding_box()
        self.assertLessEqual(abs(panel_size_before["width"] - panel_size_after["width"]), 1)
        self.assertLessEqual(abs(panel_size_before["height"] - panel_size_after["height"]), 1)
        expect(objective_data.get_by_text("—", exact=True)).to_have_count(0)
        for removed_label in [
            "隆起位置", "前端外扩", "侧面曲率", "拇指托", "无名指托", "传感器类型",
            "总按键数", "侧键数", "微动型号", "编码器类型", "编码器型号", "滚轮步数",
        ]:
            expect(objective_data.get_by_text(removed_label, exact=True)).to_have_count(0)

    def test_mouse_detail_missing_image_does_not_leave_an_extra_blank_slot(self):
        self.detail_mouse_image_url = None
        screenshot_dir = os.environ.get("E2E_MISSING_IMAGE_SCREENSHOT_DIR")

        for viewport_width in (1440, 878, 390):
            with self.subTest(viewport_width=viewport_width):
                self.page.set_viewport_size({"width": viewport_width, "height": 1000})
                self.page.goto(f"{BASE_URL}/mice/mouse-a")
                self.page.wait_for_load_state("networkidle")

                stage = self.page.locator(".detail-model-stage")
                metrics = stage.evaluate("""stage => {
                    const panel = stage.querySelector('.detail-mouse-viewport')
                    const placeholder = panel.querySelector('.compact-product-placeholder')
                    const footer = panel.querySelector('.model-panel-footer')
                    const handPanel = stage.querySelector('.detail-hand-viewport')
                    const bounds = element => {
                        const rect = element.getBoundingClientRect()
                        return { top: rect.top, bottom: rect.bottom, height: rect.height }
                    }
                    return {
                        columns: getComputedStyle(stage).gridTemplateColumns.split(' ').length,
                        stage: bounds(stage),
                        panel: bounds(panel),
                        placeholder: bounds(placeholder),
                        footer: bounds(footer),
                        handPanel: bounds(handPanel),
                    }
                }""")

                self.assertLessEqual(abs(metrics["placeholder"]["bottom"] - metrics["footer"]["top"]), 1, metrics)
                self.assertLessEqual(metrics["footer"]["height"], 100, metrics)
                if metrics["columns"] == 2:
                    self.assertLessEqual(metrics["stage"]["bottom"] - metrics["panel"]["bottom"], 2, metrics)
                    self.assertLessEqual(abs(metrics["panel"]["bottom"] - metrics["handPanel"]["bottom"]), 1, metrics)
                else:
                    self.assertLessEqual(abs(metrics["handPanel"]["top"] - metrics["panel"]["bottom"]), 1, metrics)

                if screenshot_dir:
                    os.makedirs(screenshot_dir, exist_ok=True)
                    stage.screenshot(path=os.path.join(screenshot_dir, f"mouse-detail-missing-image-{viewport_width}.png"))

    def test_mouse_detail_switches_between_image_and_specs_without_resizing_the_panel(self):
        screenshot_dir = os.environ.get("E2E_INLINE_SPECS_SCREENSHOT_DIR")

        for viewport_width in (1440, 878, 390):
            with self.subTest(viewport_width=viewport_width):
                self.page.set_viewport_size({"width": viewport_width, "height": 1000})
                self.page.goto(f"{BASE_URL}/mice/mouse-a")
                self.page.wait_for_load_state("networkidle")

                statline = self.page.locator(".hero-statline")
                stage = self.page.locator(".detail-model-stage")
                panel = self.page.locator(".detail-mouse-viewport")
                image_bounds = panel.bounding_box()
                statline_bounds = statline.bounding_box()
                stage_bounds = stage.bounding_box()
                self.assertLessEqual(statline_bounds["y"] + statline_bounds["height"], stage_bounds["y"])

                specs_tab = self.page.get_by_role("tab", name="完整参数")
                specs_tab.click()
                expect(specs_tab).to_have_attribute("aria-selected", "true")
                expect(self.page.locator("#product-specs-panel")).to_be_visible()
                specs_bounds = panel.bounding_box()
                self.assertLessEqual(abs(image_bounds["height"] - specs_bounds["height"]), 1)
                self.assertLessEqual(abs(image_bounds["width"] - specs_bounds["width"]), 1)
                self.assertIn(
                    self.page.locator("#product-specs-panel").evaluate("element => getComputedStyle(element).overflowY"),
                    ("auto", "scroll"),
                )
                first_spec_group = self.page.locator(".product-spec-group").first
                first_spec_row = first_spec_group.locator("dl > div").first
                self.assertGreater(first_spec_row.bounding_box()["height"], 20)
                self.assertTrue(first_spec_group.evaluate("element => element.open"))
                expect(first_spec_group.locator("dt small").filter(has_text="mm")).to_have_count(3)
                first_spec_group.locator("summary").click()
                expect(first_spec_row).to_be_hidden()
                first_spec_group.locator("summary").click()
                expect(first_spec_row).to_be_visible()
                tab_colors = specs_tab.evaluate("""element => ({
                    background: getComputedStyle(element).backgroundColor,
                    color: getComputedStyle(element).color,
                })""")
                self.assertNotEqual(tab_colors["background"], "rgba(0, 0, 0, 0)")

                if screenshot_dir:
                    os.makedirs(screenshot_dir, exist_ok=True)
                    self.page.locator(".detail-hero").screenshot(
                        path=os.path.join(screenshot_dir, f"mouse-detail-inline-specs-{viewport_width}.png")
                    )

    def test_signed_in_user_sees_reviews_matching_their_hand_length_first(self):
        user = {
            "id": "user-a", "email": "reviewer@example.com", "role": "USER",
            "handSize": "MEDIUM", "handLengthCm": 18.2, "preferredGripStyle": "CLAW",
        }
        self.allow_user_refresh = True
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.token', 'user-token'); sessionStorage.setItem('clicker.user', {json.dumps(json.dumps(user))});"
        )

        self.page.goto(f"{BASE_URL}/mice/mouse-a")
        self.page.wait_for_load_state("networkidle")

        grip_filter = self.page.locator(".heatmap-filter-bar select").nth(0)
        hand_filter = self.page.locator(".heatmap-filter-bar select").nth(1)
        expect(grip_filter).to_have_value("CLAW")
        expect(hand_filter).to_have_value("MEDIUM")
        expect(self.page.locator(".heatmap-heading-meta")).to_contain_text("份标记")

        desktop_screenshot = os.environ.get("E2E_HAND_MATCH_DESKTOP_SCREENSHOT")
        if desktop_screenshot:
            self.page.locator(".detail-hand-viewport").screenshot(path=desktop_screenshot)
        mobile_screenshot = os.environ.get("E2E_HAND_MATCH_MOBILE_SCREENSHOT")
        if mobile_screenshot:
            self.page.set_viewport_size({"width": 390, "height": 844})
            self.page.locator(".detail-hand-viewport").screenshot(path=mobile_screenshot)

        self.page.locator(".breadcrumb").get_by_role("link", name="鼠标库", exact=True).click()
        self.page.wait_for_url(f"{BASE_URL}/mice")
        self.page.get_by_role("link", name="查看 Razer Viper V3 Pro 详情").click()
        self.page.wait_for_url(f"{BASE_URL}/mice/mouse-a")

        expect(self.page.locator(".heatmap-filter-bar select").nth(0)).to_have_value("CLAW")
        expect(self.page.locator(".heatmap-filter-bar select").nth(1)).to_have_value("MEDIUM")
        self.assertFalse(any(path == "/api/v1/mice/undefined" for _, path, _ in self.requests))

    def test_home_hero_replaces_redundant_search_with_product_paths(self):
        self.page.goto(f"{BASE_URL}/")
        expect(self.page.locator(".quick-filters")).to_have_count(0)
        expect(self.page.locator(".home-hero [role='search']")).to_have_count(0)
        expect(self.page.get_by_role("heading", name="找到真正适合你的鼠标", exact=True)).to_be_visible()
        catalog_link = self.page.get_by_role("link", name="浏览鼠标库")
        recommendation_link = self.page.get_by_role("link", name="开始鼠标推荐", exact=True)
        expect(catalog_link).to_be_visible()
        expect(recommendation_link).to_be_visible()

        desktop_screenshot = os.environ.get("E2E_HOME_SEARCH_DESKTOP_SCREENSHOT")
        if desktop_screenshot:
            self.page.locator(".home-hero").screenshot(path=desktop_screenshot)
        mobile_screenshot = os.environ.get("E2E_HOME_SEARCH_MOBILE_SCREENSHOT")
        if mobile_screenshot:
            self.page.set_viewport_size({"width": 390, "height": 844})
            self.page.locator(".home-hero").screenshot(path=mobile_screenshot)

        catalog_link.click()
        self.page.wait_for_url(f"{BASE_URL}/mice")
        expect(self.page.get_by_role("navigation", name="主导航").get_by_role("link", name="首页", exact=True)).to_have_count(0)
        brand_link = self.page.get_by_role("banner").get_by_role("link", name="GearDB 首页")
        expect(brand_link).to_have_attribute("href", "/")
        brand_link.click()
        self.page.wait_for_url(f"{BASE_URL}/")
        self.page.get_by_role("link", name="开始鼠标推荐", exact=True).click()
        self.page.wait_for_url(f"{BASE_URL}/recommend")

    def test_home_carousel_loops_without_a_native_scrollbar(self):
        self.mice = [
            {**MICE[index % len(MICE)], "id": f"static-mouse-{index}"}
            for index in range(4)
        ]
        self.page.goto(f"{BASE_URL}/")
        static_track = self.page.locator(".trending-track")
        expect(static_track.locator(".mouse-card")).to_have_count(4, timeout=5000)
        expect(static_track.locator(".trending-set")).to_have_count(1)
        expect(self.page.get_by_text("暂停滚动", exact=True)).to_have_count(0)
        self.assertEqual(static_track.evaluate("element => getComputedStyle(element).animationName"), "none")

        self.mice = [
            {**MICE[index % len(MICE)], "id": f"latest-mouse-{index}"}
            for index in range(5)
        ]
        self.page.reload()

        carousel = self.page.locator(".trending-grid")
        track = carousel.locator(".trending-track")
        expect(track.locator(".mouse-card")).to_have_count(10, timeout=5000)
        expect(track.locator(".trending-set")).to_have_count(2)
        first_slots = track.locator(".trending-set").first.locator(".trending-card-slot")
        expect(first_slots).to_have_count(5)
        expect(self.page.get_by_text("暂停滚动", exact=True)).to_have_count(0)
        self.assertTrue(self.page.locator(".hero-copy").evaluate("element => getComputedStyle(element).animationName").startswith("home-hero-slide-in"))
        self.assertTrue(first_slots.first.evaluate("element => getComputedStyle(element).animationName").startswith("home-card-arrive"))
        self.assertGreater(
            float(first_slots.nth(1).evaluate("element => parseFloat(getComputedStyle(element).animationDelay)")),
            float(first_slots.first.evaluate("element => parseFloat(getComputedStyle(element).animationDelay)")),
        )
        self.assertEqual(carousel.evaluate("element => getComputedStyle(element).overflowX"), "hidden")
        self.assertNotEqual(track.evaluate("element => getComputedStyle(element).animationName"), "none")
        motion_screenshot_path = os.environ.get("E2E_HOME_MOTION_SCREENSHOT")
        if motion_screenshot_path:
            self.page.screenshot(path=motion_screenshot_path, full_page=True)
        screenshot_path = os.environ.get("E2E_HOME_CAROUSEL_SCREENSHOT")
        if screenshot_path:
            self.page.locator(".trending-section").screenshot(path=screenshot_path)

        carousel.hover()
        self.assertEqual(track.evaluate("element => getComputedStyle(element).animationPlayState"), "paused")
        self.page.mouse.move(1, 1)
        self.assertEqual(track.evaluate("element => getComputedStyle(element).animationPlayState"), "running")

        self.page.set_viewport_size({"width": 390, "height": 844})
        self.assertEqual(carousel.evaluate("element => getComputedStyle(element).overflowX"), "hidden")
        self.assertLessEqual(self.page.evaluate("document.documentElement.scrollWidth"), 390)
        motion_mobile_screenshot_path = os.environ.get("E2E_HOME_MOTION_MOBILE_SCREENSHOT")
        if motion_mobile_screenshot_path:
            self.page.screenshot(path=motion_mobile_screenshot_path, full_page=True)

        self.page.emulate_media(reduced_motion="reduce")
        self.assertEqual(self.page.locator(".hero-copy").evaluate("element => getComputedStyle(element).animationName"), "none")
        self.assertEqual(first_slots.first.evaluate("element => getComputedStyle(element).animationName"), "none")
        self.assertEqual(track.evaluate("element => getComputedStyle(element).animationName"), "none")
        self.page.emulate_media(reduced_motion="no-preference")

    def test_header_search_opens_the_filtered_catalog_from_home(self):
        self.page.goto(f"{BASE_URL}/")
        search_input = self.page.get_by_label("全站搜索鼠标")
        search_input.fill("v")
        search_input.press("Enter")

        self.page.wait_for_url("**/mice?q=v")
        catalog_search = self.page.get_by_placeholder("搜索鼠标", exact=True)
        expect(catalog_search).to_have_value("v")
        expect(catalog_search.locator("xpath=ancestor::label")).to_contain_text("型号")
        expect(self.page.locator("article.mouse-card")).to_have_count(1)
        expect(self.page.get_by_text("Viper V3 Pro", exact=True)).to_be_visible()

    def test_public_feedback_uses_centered_auto_dismissing_toast(self):
        self.page.set_viewport_size({"width": 1280, "height": 800})
        self.page.goto(f"{BASE_URL}/")
        self.page.get_by_role("button", name="反馈").click()
        dialog = self.page.get_by_role("dialog", name="告诉我们哪里可以更好")
        self.assertEqual(self.page.evaluate("getComputedStyle(document.documentElement).overflowY"), "hidden")
        self.assertEqual(self.page.evaluate("getComputedStyle(document.body).overflowY"), "hidden")
        feedback_body = dialog.locator(".site-feedback-body")
        feedback_scrollbar = feedback_body.evaluate("""element => ({
            width: getComputedStyle(element, '::-webkit-scrollbar').width,
            trackColor: getComputedStyle(element, '::-webkit-scrollbar-track').backgroundColor,
            thumbColor: getComputedStyle(element, '::-webkit-scrollbar-thumb').backgroundColor,
        })""")
        self.assertEqual(feedback_scrollbar["width"], "6px")
        self.assertEqual(feedback_scrollbar["trackColor"], "rgb(24, 24, 24)")
        self.assertEqual(feedback_scrollbar["thumbColor"], "rgb(85, 85, 85)")
        self.assertLessEqual(
            feedback_body.evaluate("element => element.scrollHeight"),
            feedback_body.evaluate("element => element.clientHeight"),
        )
        dialog.get_by_label("鼠标品牌 / 型号").fill("Test mouse")
        dialog.get_by_label("详细说明").fill("Visual toast regression test")
        dialog.get_by_role("button", name="提交反馈").click()

        toast = self.page.locator(".app-toast")
        expect(toast).to_be_visible(timeout=5000)
        self.page.wait_for_timeout(300)
        box = toast.bounding_box()
        self.assertIsNotNone(box)
        self.assertAlmostEqual(box["x"] + box["width"] / 2, self.page.viewport_size["width"] / 2, delta=2)
        self.assertGreaterEqual(box["y"], 12)
        expect(dialog).to_be_hidden()
        self.assertNotEqual(self.page.evaluate("getComputedStyle(document.documentElement).overflowY"), "hidden")
        self.assertNotEqual(self.page.evaluate("getComputedStyle(document.body).overflowY"), "hidden")

        self.page.wait_for_timeout(3000)
        expect(toast).to_have_count(0)

    def test_registration_creates_a_signed_in_user(self):
        self.page.goto(f"{BASE_URL}/register")
        self.page.get_by_label("邮箱", exact=True).fill("new-member@example.com")
        self.page.get_by_role("button", name="获取验证码").click()
        self.page.get_by_label("邮箱验证码").fill("123456")
        self.page.get_by_label("密码", exact=True).fill("password123")
        self.page.get_by_label("我已阅读并同意", exact=False).check()
        self.page.get_by_role("button", name="验证并创建账号 →").click()
        self.page.wait_for_url("**/mice")
        self.assertEqual(self.page.evaluate("sessionStorage.getItem('clicker.token')"), "user-token")
        registration_request = next(item for item in self.requests if item[:2] == ("POST", "/api/v1/users"))
        payload = json.loads(registration_request[2])
        self.assertEqual(payload["verificationCode"], "123456")
        self.assertTrue(payload["acceptedTerms"])

    def test_signed_in_user_can_submit_a_grip_support_map(self):
        user = {
            "id": "user-a", "email": "reviewer@example.com", "role": "USER",
            "handSize": "MEDIUM", "handLengthCm": 18.2, "preferredGripStyle": "CLAW",
        }
        self.allow_user_refresh = True
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.token', 'user-token'); sessionStorage.setItem('clicker.user', {json.dumps(json.dumps(user))});"
        )
        self.page.goto(f"{BASE_URL}/mice/mouse-a")
        self.page.get_by_role("button", name="标记支撑位置").click()
        dialog = self.page.get_by_role("dialog", name="标记 Viper V3 Pro 的支撑位置")
        expect(dialog).to_be_visible()
        self.page.keyboard.press("Escape")
        expect(dialog).to_be_hidden()
        self.page.get_by_role("button", name="标记支撑位置").click()
        dialog.get_by_role("tab", name=re.compile("抓握")).click()
        support_model = dialog.locator(".hand-support-2d")
        expect(support_model).to_have_class(re.compile(r"\bis-ready\b"), timeout=10000)
        support_canvas = support_model.locator('canvas[aria-label="可涂抹的抓握个人支撑位置图"]')
        bounds = support_canvas.bounding_box()
        self.assertIsNotNone(bounds)
        support_canvas.click(position={"x": bounds["width"] * 0.5, "y": bounds["height"] * 0.65})
        dialog.get_by_role("button", name="保存抓握支撑位置").click()
        expect(self.page.get_by_text("抓握支撑位置已保存", exact=True)).to_be_visible()
        self.assertFalse(any(path.startswith("/api/v1/mice/mouse-a/reviews/mine/grip-scores/") for _, path, _ in self.requests))
        support_request = next(item for item in self.requests if item[:2] == ("PUT", "/api/v1/mice/mouse-a/reviews/mine/support-positions/CLAW"))
        self.assertGreater(len(json.loads(support_request[2])["dabs"]), 0)

    def test_completed_grip_support_maps_remain_readable_on_mobile(self):
        user = {
            "id": "user-a", "email": "reviewer@example.com", "role": "USER",
            "handSize": "MEDIUM", "handLengthCm": 18.2, "preferredGripStyle": "CLAW",
        }
        self.allow_user_refresh = True
        self.review_saved = True
        for grip_style in ("PALM", "CLAW", "FINGERTIP", "MIXED"):
            self.personal_support_dabs_by_grip[grip_style] = [{"x": 500, "y": 650, "radius": 70, "mode": "PAINT"}]
        self.page.set_viewport_size({"width": 390, "height": 844})
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.token', 'user-token'); sessionStorage.setItem('clicker.user', {json.dumps(json.dumps(user))});"
        )
        self.page.goto(f"{BASE_URL}/mice/mouse-a")
        manage_review = self.page.get_by_role("button", name="管理我的支撑记录")
        expect(manage_review).to_be_visible()
        manage_review_box = manage_review.bounding_box()
        self.assertIsNotNone(manage_review_box)
        self.assertLessEqual(manage_review_box["x"] + manage_review_box["width"], 390)
        manage_review.click()

        grip_tabs = self.page.locator(".support-grip-tabs button")
        expect(grip_tabs).to_have_count(4)
        expect(self.page.locator(".support-grip-tabs").get_by_text("已涂抹", exact=True)).to_have_count(4)
        self.assertTrue(grip_tabs.evaluate_all(
            "tabs => tabs.every(tab => tab.scrollWidth <= tab.clientWidth && parseFloat(getComputedStyle(tab.querySelector('small')).fontSize) >= 12)"
        ))
        dialog_box = self.page.locator(".review-dialog").bounding_box()
        self.assertIsNotNone(dialog_box)
        self.assertLessEqual(dialog_box["width"], 390)
        screenshot_path = os.environ.get("E2E_READABILITY_SCREENSHOT")
        if screenshot_path:
            self.page.locator(".support-grip-tabs").screenshot(path=screenshot_path)

    def test_public_support_heatmap_is_separate_from_my_editable_map(self):
        user = {
            "id": "user-a", "email": "reviewer@example.com", "role": "USER",
            "handSize": "MEDIUM", "handLengthCm": 18.2, "preferredGripStyle": "CLAW",
        }
        self.allow_user_refresh = True
        self.review_saved = True
        self.personal_support_dabs_by_grip["CLAW"] = [{"x": 500, "y": 650, "radius": 70, "mode": "PAINT"}]
        self.support_summary = {
            "sampleCount": 12,
            "positions": [],
            "cells": [{"x": 31, "y": 62, "count": 8, "percentage": 67}],
            "maxCount": 8,
            "gridColumns": 64,
            "gridRows": 96,
        }
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.token', 'user-token'); sessionStorage.setItem('clicker.user', {json.dumps(json.dumps(user))});"
        )
        self.page.goto(f"{BASE_URL}/mice/mouse-a")

        expect(self.page.locator(".detail-hand-viewport .public-support-map")).to_have_count(1)
        expect(self.page.locator(".detail-hand-viewport .support-tools")).to_have_count(0)
        expect(self.page.locator(".detail-hand-viewport .heatmap-filter-bar")).to_be_visible()
        expect(self.page.locator(".detail-hand-viewport .heatmap-score")).to_have_count(0)
        expect(self.page.locator('.public-support-map canvas[aria-label="所有用户支撑位置热力图"]')).to_be_visible(timeout=10000)
        self.assertEqual(
            self.page.locator(".detail-model-stage").evaluate(
                "element => getComputedStyle(element).gridTemplateColumns.split(' ').length"
            ),
            2,
        )
        footer_alignment = self.page.evaluate("""() => {
            const left = document.querySelector('.detail-mouse-viewport .model-panel-footer').getBoundingClientRect()
            const right = document.querySelector('.detail-hand-viewport .model-panel-footer').getBoundingClientRect()
            return { topDelta: Math.abs(left.top - right.top), bottomDelta: Math.abs(left.bottom - right.bottom) }
        }""")
        self.assertLessEqual(footer_alignment["bottomDelta"], 1)
        expect(self.page.locator(".detail-mouse-viewport .detail-product-image")).to_be_visible()

        detail_desktop_screenshot = os.environ.get("E2E_DETAIL_MODEL_DESKTOP_SCREENSHOT")
        if detail_desktop_screenshot:
            self.page.locator(".detail-hero").screenshot(path=detail_desktop_screenshot)
        detail_mobile_screenshot = os.environ.get("E2E_DETAIL_MODEL_MOBILE_SCREENSHOT")
        self.page.set_viewport_size({"width": 390, "height": 844})
        self.assertEqual(
            self.page.locator(".detail-model-stage").evaluate(
                "element => getComputedStyle(element).gridTemplateColumns.split(' ').length"
            ),
            1,
        )
        self.assertLessEqual(
            self.page.evaluate("document.documentElement.scrollWidth"),
            390,
        )
        if detail_mobile_screenshot:
            self.page.locator(".detail-hero").screenshot(path=detail_mobile_screenshot)
        self.page.set_viewport_size({"width": 1280, "height": 720})
        heatmap_controls_screenshot = os.environ.get("E2E_DETAIL_HEATMAP_SCREENSHOT")
        if heatmap_controls_screenshot:
            self.page.locator(".detail-hand-viewport").screenshot(path=heatmap_controls_screenshot)

        self.page.get_by_role("button", name="管理我的支撑记录").click()
        expect(self.page.locator(".personal-support-editor")).to_be_visible()
        expect(self.page.locator(".personal-support-editor .hand-support-2d")).to_have_count(1)
        self.page.locator(".support-grip-tabs").get_by_role("tab", name=re.compile("抓握")).click()
        expect(self.page.locator('.personal-support-editor canvas[aria-label="可涂抹的抓握个人支撑位置图"]')).to_be_visible()
        expect(self.page.locator(".personal-support-editor .support-tools")).to_be_visible()

        desktop_screenshot = os.environ.get("E2E_SUPPORT_DESKTOP_SCREENSHOT")
        if desktop_screenshot:
            self.page.locator(".review-dialog").screenshot(path=desktop_screenshot)
        mobile_screenshot = os.environ.get("E2E_SUPPORT_MOBILE_SCREENSHOT")
        if mobile_screenshot:
            self.page.set_viewport_size({"width": 390, "height": 844})
            self.page.locator(".personal-support-editor").screenshot(path=mobile_screenshot)

    def test_personal_support_map_supports_pointer_editing(self):
        user = {
            "id": "user-a", "email": "reviewer@example.com", "role": "USER",
            "handSize": "MEDIUM", "handLengthCm": 18.2, "preferredGripStyle": "CLAW",
        }
        self.allow_user_refresh = True
        self.review_saved = True
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.token', 'user-token'); sessionStorage.setItem('clicker.user', {json.dumps(json.dumps(user))});"
        )
        self.page.goto(f"{BASE_URL}/mice/mouse-a")
        self.page.get_by_role("button", name="管理我的支撑记录").click()

        model = self.page.locator(".personal-support-editor .hand-support-2d")
        expect(model).to_have_class(re.compile(r"\bis-ready\b"), timeout=10000)
        canvas = model.locator("canvas")
        self.assertIsNone(canvas.get_attribute("tabindex"))
        self.assertIsNone(canvas.get_attribute("aria-describedby"))
        canvas.scroll_into_view_if_needed()
        box = canvas.bounding_box()
        self.assertIsNotNone(box)
        center_x = box["x"] + box["width"] / 2
        center_y = box["y"] + box["height"] / 2
        support_status = self.page.locator(".personal-support-editor .support-selection-status > strong")
        expect(support_status).to_have_text("尚未涂抹支撑区域")
        self.page.mouse.click(center_x, center_y, button="left")
        expect(support_status).to_contain_text("已涂抹约")

        self.page.get_by_role("button", name="清空", exact=True).click()
        expect(support_status).to_have_text("尚未涂抹支撑区域")

    def test_admin_can_preview_and_commit_a_csv_import(self):
        admin = {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"}
        self.allow_admin_refresh = True
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.admin.token', 'admin-token'); sessionStorage.setItem('clicker.admin.user', {json.dumps(json.dumps(admin))});"
        )
        self.page.goto(f"{BASE_URL}/admin")
        overview_screenshot = os.environ.get("E2E_ADMIN_OVERVIEW_SCREENSHOT")
        if overview_screenshot:
            self.page.wait_for_load_state("networkidle")
            self.page.wait_for_timeout(350)
            self.page.screenshot(path=overview_screenshot, full_page=True)
        self.page.get_by_role("button", name="鼠标资产").click()
        csv = (
            "brand,model,variant,slug,status,sizeCategory,lengthMm,widthMm,heightMm,weightG,shapeType,sensorName,maxDpi,maxPollingRateHz,connectionModes,primarySourceUrl\n"
            "测试品牌,测试型号,,e2e-import,DRAFT,MEDIUM,120,62,39,58,SYMMETRICAL,PAW3395,26000,1000,wired,https://example.com/e2e-import\n"
        ).encode("utf-8")
        self.page.locator('input[type="file"][accept*=".csv"]').set_input_files({
            "name": "mice.csv", "mimeType": "text/csv", "buffer": csv,
        })
        preview_dialog = self.page.get_by_role("dialog", name="CSV 导入预检")
        expect(preview_dialog).to_be_visible()
        expect(preview_dialog.get_by_text("1 行通过校验", exact=True)).to_be_visible()
        expect(self.page.get_by_text("mice.csv", exact=True)).to_be_visible()
        self.page.get_by_role("button", name="确认写入数据库").click()
        expect(self.page.get_by_text("导入完成：新增 1 条，更新 0 条", exact=True)).to_be_visible()
        self.assertTrue(self.import_committed)
        self.assertTrue(any(item[:2] == ("POST", "/api/v1/admin/mice/imports/preview") for item in self.requests))
        self.assertTrue(any(item[:2] == ("POST", "/api/v1/admin/mice/imports") for item in self.requests))

    def test_admin_route_rejects_a_stale_user_without_a_token(self):
        stale_admin = {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"}
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.admin.user', {json.dumps(json.dumps(stale_admin))}); sessionStorage.removeItem('clicker.admin.token');"
        )

        self.page.goto(f"{BASE_URL}/admin")

        self.page.wait_for_url(f"{BASE_URL}/admin/login")
        expect(self.page.get_by_role("heading", name="登录管理后台")).to_be_visible()
        expect(self.page.get_by_role("button", name="退出后台")).to_have_count(0)

    def test_admin_dashboard_reloads_after_logging_in_again(self):
        admin = {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"}
        self.allow_admin_refresh = True
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.admin.token', 'admin-token'); sessionStorage.setItem('clicker.admin.user', {json.dumps(json.dumps(admin))});"
        )
        self.page.goto(f"{BASE_URL}/admin")
        first_metric = self.page.locator(".metric-grid article").first.locator("strong")
        expect(first_metric).to_have_text("4")

        self.page.get_by_role("button", name="退出后台").click()
        self.page.wait_for_url(f"{BASE_URL}/admin/login")
        self.dashboard_mice_total = 9
        self.page.get_by_label("邮箱").fill("admin@example.com")
        self.page.get_by_label("密码").fill("password123")
        self.page.get_by_role("button", name="登录 →").click()
        expect(self.page.get_by_role("heading", name="验证管理员身份")).to_be_visible()
        self.page.get_by_label("邮箱验证码").fill("123456")
        self.page.get_by_role("button", name="验证并进入后台 →").click()

        self.page.wait_for_url(f"{BASE_URL}/admin")
        expect(self.page.locator(".metric-grid article").first.locator("strong")).to_have_text("9")

    def test_administrator_can_log_into_frontend_without_a_verification_code(self):
        self.page.goto(f"{BASE_URL}/login")
        self.page.get_by_label("邮箱").fill("admin@example.com")
        self.page.get_by_label("密码", exact=True).fill("password123")
        self.page.get_by_role("button", name="登录 →").click()

        self.page.wait_for_url(f"{BASE_URL}/mice")
        self.assertEqual(self.page.evaluate("sessionStorage.getItem('clicker.token')"), "user-token")
        expect(self.page.get_by_text("验证管理员身份", exact=True)).to_have_count(0)
        self.assertTrue(any(item[:2] == ("POST", "/api/v1/sessions") for item in self.requests))
        self.assertFalse(any(item[:2] == ("POST", "/api/v1/admin-sessions") for item in self.requests))

    def test_admin_editor_shows_live_publication_checklist(self):
        admin = {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"}
        self.allow_admin_refresh = True
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.admin.token', 'admin-token'); sessionStorage.setItem('clicker.admin.user', {json.dumps(json.dumps(admin))});"
        )
        self.page.goto(f"{BASE_URL}/admin")
        self.page.get_by_role("button", name="鼠标资产").click()
        self.page.get_by_role("button", name="＋ 新增鼠标").click()
        expect(self.page.get_by_text("发布前检查", exact=True)).to_be_visible()
        expect(self.page.get_by_text("草稿可以继续保存；发布前还需补全", exact=False)).to_be_visible()
        expect(self.page.locator(".publication-checklist li").filter(has_text="数据来源 URL")).to_be_visible()
        editor = self.page.locator(".editor-modal")
        for removed_label in [
            "隆起位置", "前端外扩", "侧面曲率", "拇指托", "无名指托", "传感器类型",
            "侧键数量", "总按键数量", "微动型号", "编码器型号", "编码器类型", "编码器步数",
        ]:
            expect(editor.get_by_text(removed_label, exact=True)).to_have_count(0)
        screenshot_path = os.environ.get("E2E_PUBLICATION_CHECKLIST_SCREENSHOT")
        if screenshot_path:
            self.page.locator(".publication-checklist").screenshot(path=screenshot_path)

    def test_admin_can_crop_transform_preview_and_upload_an_image(self):
        admin = {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"}
        self.allow_admin_refresh = True
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.admin.token', 'admin-token'); sessionStorage.setItem('clicker.admin.user', {json.dumps(json.dumps(admin))});"
        )
        self.page.goto(f"{BASE_URL}/admin")
        self.page.wait_for_load_state("networkidle")
        self.page.get_by_role("button", name="鼠标资产").click()
        self.page.get_by_role("button", name="＋ 新增鼠标").click()
        self.page.locator(".image-file-input").set_input_files({
            "name": "mouse.png", "mimeType": "image/png", "buffer": TEST_PNG,
        })

        editor = self.page.get_by_role("dialog", name="截选前台卡片图片")
        expect(editor).to_be_visible()
        expect(editor.get_by_text("前台效果预览", exact=True)).to_be_visible()
        expect(editor.get_by_text("截选比例固定为 16:9", exact=False)).to_be_visible()
        editor.get_by_role("button", name="向右旋转").click()
        editor.get_by_role("button", name="水平翻转").click()
        editor.get_by_label("图片缩放比例").fill("1.4")
        expect(editor.get_by_text("140%", exact=True)).to_be_visible()

        screenshot_path = os.environ.get("E2E_IMAGE_EDITOR_SCREENSHOT")
        if screenshot_path:
            editor.screenshot(path=screenshot_path)

        self.page.set_viewport_size({"width": 390, "height": 844})
        expect(editor.get_by_role("button", name="保存并使用图片")).to_be_visible()
        mobile_screenshot_path = os.environ.get("E2E_IMAGE_EDITOR_MOBILE_SCREENSHOT")
        if mobile_screenshot_path:
            self.page.screenshot(path=mobile_screenshot_path, full_page=True)

        editor.get_by_role("button", name="保存并使用图片").click()
        expect(editor).not_to_be_visible()
        expect(self.page.locator(".image-picker-preview > img")).to_have_attribute("src", "/api/v1/images/edited.webp")
        self.assertTrue(any(item[:2] == ("POST", "/api/v1/admin/images") for item in self.requests))

    def test_admin_review_and_audit_details_open_as_floating_windows(self):
        admin = {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"}
        self.allow_admin_refresh = True
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.admin.token', 'admin-token'); sessionStorage.setItem('clicker.admin.user', {json.dumps(json.dumps(admin))});"
        )
        self.page.goto(f"{BASE_URL}/admin")
        self.page.locator(".admin-sidebar nav button").filter(has_text="支撑记录").click()
        groups = self.page.locator(".review-mouse-group")
        expect(groups).to_have_count(2)
        razer_group = groups.filter(has_text="Razer Viper V3 Pro")
        expect(razer_group.locator(".group-stat-total")).to_contain_text("3已有评价")
        expect(razer_group.locator(".group-stat-pending")).to_contain_text("1待审核")
        expect(razer_group.locator(".group-stat-anomaly")).to_contain_text("2异常")
        expect(razer_group.locator(".group-stat-disabled")).to_contain_text("1已停用")
        expect(razer_group.get_by_role("button", name="查看与处理")).to_have_count(0)
        razer_group.locator(".review-mouse-group-head").click()
        expect(razer_group.get_by_role("button", name="查看与处理")).to_have_count(3)
        group_screenshot_path = os.environ.get("E2E_ADMIN_REVIEW_GROUP_SCREENSHOT")
        if group_screenshot_path:
            self.page.screenshot(path=group_screenshot_path, full_page=True)

        self.page.set_viewport_size({"width": 390, "height": 844})
        expect(razer_group.locator(".review-mouse-review-action").first).to_be_visible()
        page_widths = self.page.evaluate("() => ({ viewport: window.innerWidth, page: document.documentElement.scrollWidth })")
        self.assertLessEqual(page_widths["page"], page_widths["viewport"])
        mobile_group_screenshot_path = os.environ.get("E2E_ADMIN_REVIEW_GROUP_MOBILE_SCREENSHOT")
        if mobile_group_screenshot_path:
            self.page.screenshot(path=mobile_group_screenshot_path, full_page=True)
        self.page.set_viewport_size({"width": 1280, "height": 720})
        razer_group.get_by_role("button", name="查看与处理").first.click()

        review_dialog = self.page.get_by_role("dialog", name="支撑记录查看与处理")
        expect(review_dialog).to_be_visible()
        expect(review_dialog.get_by_text("支撑涂抹结果", exact=True)).to_be_visible()
        expect(review_dialog.locator(".review-detail-row")).to_have_count(0)
        expect(review_dialog.get_by_text("PALM_CENTER", exact=False)).to_have_count(0)
        expect(review_dialog.locator(".review-hand-model canvas")).to_be_visible()
        expect(review_dialog.locator(".hand-support-3d")).to_have_class(re.compile("is-ready"), timeout=15000)
        expect(review_dialog.get_by_text("举报反馈", exact=True)).to_be_visible()
        expect(review_dialog.get_by_text("该支撑记录的涂抹范围疑似异常。", exact=True)).to_be_visible()
        review_dialog.get_by_role("button", name="标记已解决").click()
        expect(review_dialog.get_by_text("已解决", exact=True)).to_be_visible()
        self.assertTrue(any(item[:2] == ("PATCH", "/api/v1/admin/reports/report-review-a") for item in self.requests))
        screenshot_path = os.environ.get("E2E_ADMIN_REVIEW_3D_SCREENSHOT")
        if screenshot_path:
            review_dialog.screenshot(path=screenshot_path)
        mobile_screenshot_path = os.environ.get("E2E_ADMIN_REVIEW_3D_MOBILE_SCREENSHOT")
        if mobile_screenshot_path:
            self.page.set_viewport_size({"width": 390, "height": 844})
            expect(review_dialog.get_by_role("button", name="关闭窗口")).to_be_visible()
            self.page.screenshot(path=mobile_screenshot_path, full_page=True)
        review_dialog.get_by_role("button", name="关闭窗口").click()
        expect(review_dialog).not_to_be_visible()

        self.page.get_by_role("button", name="操作审计").click()
        self.page.get_by_role("button", name="查看变更").click()
        audit_dialog = self.page.get_by_role("dialog", name="更新鼠标")
        expect(audit_dialog).to_be_visible()
        expect(audit_dialog.get_by_text('"weightG": 55', exact=False)).to_be_visible()
        audit_dialog.get_by_role("button", name="关闭窗口").click()
        expect(audit_dialog).not_to_be_visible()

    def test_admin_inline_tools_are_replaced_by_floating_windows(self):
        admin = {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"}
        self.allow_admin_refresh = True
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.admin.token', 'admin-token'); sessionStorage.setItem('clicker.admin.user', {json.dumps(json.dumps(admin))});"
        )
        self.page.goto(f"{BASE_URL}/admin")

        self.page.get_by_role("button", name="鼠标资产").click()
        self.page.get_by_role("button", name="＋ 新增鼠标").click()
        self.page.get_by_role("button", name="从项目图片库选择").click()
        library_dialog = self.page.get_by_role("dialog", name="项目图片库")
        expect(library_dialog).to_be_visible()
        library_dialog.get_by_role("button", name="关闭悬浮窗").click()
        self.page.locator(".editor-modal .editor-heading > button").click()

        self.page.get_by_role("button", name="品牌中心").click()
        self.page.locator(".brand-list > button").first.click()
        brand_dialog = self.page.get_by_role("dialog", name="编辑品牌")
        expect(brand_dialog).to_be_visible()
        brand_dialog.get_by_role("button", name="关闭悬浮窗").click()

        self.page.get_by_role("button", name="反馈工单").click()
        self.page.get_by_role("button", name="处理工单").click()
        report_dialog = self.page.get_by_role("dialog", name="处理反馈工单")
        expect(report_dialog).to_be_visible()
        expect(report_dialog.get_by_text("重量参数可能不准确", exact=False)).to_be_visible()
        report_dialog.get_by_role("button", name="取消处理").click()
        expect(report_dialog).not_to_be_visible()

    def test_admin_can_change_user_role_then_ban_the_account(self):
        admin = {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"}
        self.allow_admin_refresh = True
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.admin.token', 'admin-token'); sessionStorage.setItem('clicker.admin.user', {json.dumps(json.dumps(admin))});"
        )
        self.page.goto(f"{BASE_URL}/admin")
        self.page.get_by_role("button", name="用户管理").click()
        expect(self.page.get_by_text("managed@example.com", exact=True)).to_be_visible()
        self.page.get_by_role("button", name="管理用户").click()
        expect(self.page.get_by_role("dialog", name="管理用户")).to_be_visible()
        expect(self.page.locator(".user-management-modal")).to_be_visible()

        self.page.get_by_label("目标角色").select_option("ADMIN")
        self.page.get_by_label("调整原因").fill("负责鼠标数据维护")
        self.page.get_by_role("button", name="保存角色变更").click()
        role_dialog = self.page.get_by_role("dialog", name="变更用户角色")
        expect(role_dialog).to_be_visible()
        expect(role_dialog.get_by_text("负责鼠标数据维护", exact=False)).to_be_visible()
        role_dialog.get_by_role("button", name="调整为管理员").click()
        expect(self.page.get_by_text("managed@example.com 已调整为管理员", exact=True)).to_be_visible()
        expect(self.page.get_by_text("管理员账号受保护；如需封禁", exact=False)).to_be_visible()

        self.page.get_by_label("目标角色").select_option("USER")
        self.page.get_by_label("调整原因").fill("结束临时数据维护")
        self.page.get_by_role("button", name="保存角色变更").click()
        role_dialog = self.page.get_by_role("dialog", name="变更用户角色")
        role_dialog.get_by_role("button", name="调整为普通用户").click()
        expect(self.page.get_by_text("managed@example.com 已调整为普通用户", exact=True)).to_be_visible()

        screenshot_path = os.environ.get("E2E_USER_MANAGEMENT_SCREENSHOT")
        if screenshot_path:
            self.page.locator(".user-management-editor").screenshot(path=screenshot_path)

        self.page.get_by_label("处理原因").fill("异常登录行为")
        self.page.get_by_role("button", name="确认封禁用户").click()
        ban_dialog = self.page.get_by_role("dialog", name="封禁用户")
        expect(ban_dialog.get_by_text("异常登录行为", exact=False)).to_be_visible()
        ban_dialog.get_by_role("button", name="确认封禁用户").click()
        expect(self.page.locator("em.status-disabled")).to_have_text("已封禁")
        expect(self.page.get_by_text("异常登录行为", exact=True)).to_be_visible()
        role_requests = [item for item in self.requests if item[:2] == ("PATCH", "/api/v1/admin/users/managed-user/role")]
        self.assertEqual([json.loads(item[2])["role"] for item in role_requests], ["ADMIN", "USER"])
        ban_request = next(item for item in self.requests if item[:2] == ("PATCH", "/api/v1/admin/users/managed-user"))
        self.assertEqual(json.loads(ban_request[2]), {"status": "DISABLED", "reason": "异常登录行为"})

        self.page.get_by_role("button", name="管理用户").click()
        self.page.get_by_label("处理原因").fill("复核通过")
        self.page.get_by_role("button", name="确认解除封禁").click()
        unban_dialog = self.page.get_by_role("dialog", name="解除封禁用户")
        unban_dialog.get_by_role("button", name="确认解除封禁用户").click()
        expect(self.page.locator("em.status-active")).to_have_text("正常")
        status_requests = [json.loads(item[2]) for item in self.requests if item[:2] == ("PATCH", "/api/v1/admin/users/managed-user")]
        self.assertEqual(status_requests[-1], {"status": "ACTIVE", "reason": "复核通过"})

    def test_admin_expansion_workspaces_are_connected(self):
        admin = {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"}
        self.allow_admin_refresh = True
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.admin.token', 'admin-token'); sessionStorage.setItem('clicker.admin.user', {json.dumps(json.dumps(admin))});"
        )
        self.page.goto(f"{BASE_URL}/admin")
        self.page.get_by_role("button", name=re.compile("运营分析")).click()
        expect(self.page.get_by_role("heading", name="运营分析与通知")).to_be_visible()
        self.page.get_by_role("button", name=re.compile("品牌中心")).click()
        expect(self.page.get_by_role("heading", name="品牌资料中心")).to_be_visible()
        expect(self.page.get_by_text("Logitech", exact=True)).to_be_visible()
        self.page.get_by_role("button", name=re.compile("反馈工单")).click()
        expect(self.page.get_by_role("heading", name="前台反馈与数据纠错")).to_be_visible()
        status_select = self.page.locator(".toolbar select").last
        status_select.click()
        admin_popup = self.page.locator(".select-enhancer-popup")
        expect(admin_popup).to_be_visible()
        self.assertTrue(admin_popup.evaluate("element => element.classList.contains('is-admin')"))
        self.page.keyboard.press("Escape")
        expect(self.page.locator(".report-board > article")).to_have_count(1)
        expect(self.page.locator(".report-priority")).to_have_text("需关注")
        expect(self.page.locator(".report-status")).to_contain_text("待处理")
        expect(self.page.get_by_text("该支撑记录的涂抹范围疑似异常。", exact=True)).to_have_count(0)
        self.assertEqual(
            self.page.locator(".report-board").evaluate("element => getComputedStyle(element).gridTemplateColumns.split(' ').length"),
            1,
        )
        feedback_screenshot = os.environ.get("E2E_ADMIN_FEEDBACK_SCREENSHOT")
        if feedback_screenshot:
            self.page.screenshot(path=feedback_screenshot, full_page=True)
        self.page.get_by_role("button", name=re.compile("系统运营")).click()
        expect(self.page.get_by_role("heading", name="系统运营", level=2)).to_be_visible()
        self.assertTrue(any(item[:2] == ("GET", "/api/v1/admin/analytics") for item in self.requests))
        self.assertTrue(any(item[:2] == ("GET", "/api/v1/admin/settings") for item in self.requests))

    def test_admin_setting_is_reflected_on_the_open_frontend(self):
        admin = {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"}
        user = {
            "id": "user-a", "email": "reviewer@example.com", "role": "USER",
            "handSize": "MEDIUM", "handLengthCm": 18.2, "preferredGripStyle": "CLAW",
        }
        self.allow_admin_refresh = True
        self.allow_user_refresh = True
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.admin.token', 'admin-token'); "
            f"sessionStorage.setItem('clicker.admin.user', {json.dumps(json.dumps(admin))}); "
            f"sessionStorage.setItem('clicker.token', 'user-token'); "
            f"sessionStorage.setItem('clicker.user', {json.dumps(json.dumps(user))});"
        )
        self.page.goto(f"{BASE_URL}/admin")
        self.page.get_by_role("button", name=re.compile("系统运营")).click()
        setting_row = self.page.locator(".settings-card > label").filter(has_text="开放支撑记录提交")
        setting_row.locator("select").select_option("false")
        setting_row.get_by_role("button", name="保存").click()
        expect(self.page.get_by_text("系统设置已生效", exact=True)).to_be_visible()

        self.page.evaluate("""
            window.dispatchEvent(new CustomEvent('clicker:realtime', {
              detail: { type: 'settings.changed', mouseId: null, occurredAt: new Date().toISOString() }
            }))
        """)
        self.page.wait_for_timeout(250)
        self.page.goto(f"{BASE_URL}/mice/mouse-a")
        expect(self.page.get_by_text("支撑记录提交暂时关闭", exact=True)).to_be_visible()
        self.assertTrue(any(item[:2] == ("PUT", "/api/v1/admin/settings/reviews.enabled") for item in self.requests))

    def test_compare_selection_recovers_from_local_storage(self):
        self.support_summary = {
            "sampleCount": 12,
            "positions": [],
            "cells": [{"x": 31, "y": 62, "count": 8, "percentage": 67}],
            "maxCount": 8,
            "gridColumns": 64,
            "gridRows": 96,
        }
        selected = [{"id": mouse["id"], "displayName": mouse["displayName"]} for mouse in MICE]
        serialized = json.dumps(selected, ensure_ascii=False)
        self.page.add_init_script(
            f"localStorage.setItem('clicker.compare', {json.dumps(serialized)})"
        )
        self.page.goto(f"{BASE_URL}/compare")
        self.page.wait_for_load_state("networkidle")
        expect(self.page.locator(".selected-list .selected-mouse-row")).to_have_count(2)
        support_comparison = self.page.locator(".support-comparison")
        expect(support_comparison.get_by_role("heading", name="支撑位置对比")).to_be_visible()
        expect(support_comparison.locator(".support-compare-card")).to_have_count(2)
        expect(support_comparison.locator("canvas")).to_have_count(2)
        expect(support_comparison).to_contain_text("12 份记录")
        expect(self.page.locator(".comparison-table")).to_contain_text("+6g")
        self.assertTrue(support_comparison.evaluate(
            "element => Boolean(element.compareDocumentPosition(document.querySelector('.comparison-wrap')) & Node.DOCUMENT_POSITION_FOLLOWING)"
        ))
        compare_support_screenshot = os.environ.get("E2E_COMPARE_SUPPORT_SCREENSHOT")
        if compare_support_screenshot:
            support_comparison.screenshot(path=compare_support_screenshot)
        support_comparison.get_by_label("筛选支撑位置握姿").select_option("CLAW")
        expect(support_comparison.locator(".support-compare-card footer").first).to_contain_text("抓握")
        self.assertEqual(parse_qs(urlparse(self.page.url).query).get("ids"), ["mouse-a,mouse-b"])

        self.page.set_viewport_size({"width": 390, "height": 844})
        comparison = self.page.locator(".comparison-wrap")
        self.assertEqual(
            self.page.locator(".comparison-table thead").evaluate("element => getComputedStyle(element).display"),
            "none",
        )
        self.assertLessEqual(
            comparison.evaluate("element => element.scrollWidth"),
            comparison.evaluate("element => element.clientWidth"),
        )
        self.assertLessEqual(
            support_comparison.evaluate("element => element.scrollWidth"),
            support_comparison.evaluate("element => element.clientWidth"),
        )

    def test_compare_route_keeps_the_page_width_stable_without_overflow(self):
        self.page.set_viewport_size({"width": 1280, "height": 1000})
        self.page.goto(f"{BASE_URL}/")
        self.page.wait_for_load_state("networkidle")
        home_metrics = self.page.evaluate("""() => ({
            clientWidth: document.documentElement.clientWidth,
            clientHeight: document.documentElement.clientHeight,
            scrollHeight: document.documentElement.scrollHeight,
        })""")
        self.assertGreater(home_metrics["scrollHeight"], home_metrics["clientHeight"])

        self.page.locator(".main-nav").get_by_role("link", name="参数对比").click()
        self.page.wait_for_url("**/compare")
        expect(self.page.get_by_role("heading", name="并排参数对比")).to_be_visible()
        compare_metrics = self.page.evaluate("""() => ({
            clientWidth: document.documentElement.clientWidth,
            clientHeight: document.documentElement.clientHeight,
            scrollHeight: document.documentElement.scrollHeight,
            scrollbarGutter: getComputedStyle(document.documentElement).scrollbarGutter,
        })""")
        self.assertLessEqual(compare_metrics["scrollHeight"], compare_metrics["clientHeight"])
        self.assertEqual(compare_metrics["scrollbarGutter"], "stable both-edges")
        self.assertEqual(compare_metrics["clientWidth"], home_metrics["clientWidth"])

    def test_forgotten_password_can_be_reset_from_the_login_page(self):
        self.page.goto(f"{BASE_URL}/login")
        self.page.get_by_role("link", name="忘记密码？").click()
        self.page.wait_for_url("**/forgot-password")
        self.page.get_by_label("邮箱").fill("member@example.com")
        self.page.get_by_role("button", name="获取重置验证码").click()
        expect(self.page.get_by_text("验证码已发送", exact=False)).to_be_visible()
        self.page.get_by_label("邮箱验证码").fill("123456")
        self.page.get_by_label("新密码", exact=True).fill("new-password123")
        self.page.get_by_label("确认新密码", exact=True).fill("new-password123")
        self.page.get_by_role("button", name="确认重置密码").click()
        expect(self.page.get_by_text("密码重置成功，请使用新密码登录", exact=True)).to_be_visible()
        expect(self.page.get_by_role("link", name="返回登录")).to_be_visible()

    def test_recommendations_show_exact_and_explained_near_matches(self):
        self.page.set_viewport_size({"width": 1440, "height": 900})
        self.page.goto(f"{BASE_URL}/recommend")
        expect(self.page.locator('canvas[aria-label="可涂抹期望支撑位置的二维右手掌面图"]')).to_be_visible(timeout=10000)
        default_metrics = self.page.evaluate("""() => ({
            clientHeight: document.documentElement.clientHeight,
            scrollHeight: document.documentElement.scrollHeight,
        })""")
        self.assertLessEqual(default_metrics["scrollHeight"], default_metrics["clientHeight"])
        recommendation_default_screenshot = os.environ.get("E2E_RECOMMENDATION_DEFAULT_SCREENSHOT")
        if recommendation_default_screenshot:
            self.page.wait_for_timeout(700)
            self.page.screenshot(path=recommendation_default_screenshot, full_page=False)
        self.page.set_viewport_size({"width": 390, "height": 844})
        mobile_order = self.page.evaluate("""() => [
            document.querySelector('.recommendation-controls').getBoundingClientRect().top,
            document.querySelector('.recommendation-hand-panel').getBoundingClientRect().top,
            document.querySelector('.recommendation-submit-panel').getBoundingClientRect().top,
        ]""")
        self.assertEqual(mobile_order, sorted(mobile_order))
        self.assertLessEqual(self.page.evaluate("document.documentElement.scrollWidth"), 390)
        self.page.set_viewport_size({"width": 1440, "height": 900})
        self.page.locator(".recommendation-grips button").filter(has_text="抓握").click()
        support_canvas = self.page.locator('canvas[aria-label="可涂抹期望支撑位置的二维右手掌面图"]')
        expect(support_canvas).to_be_visible(timeout=10000)
        bounds = support_canvas.bounding_box()
        self.assertIsNotNone(bounds)
        support_canvas.click(position={"x": bounds["width"] * 0.5, "y": bounds["height"] * 0.61})
        support_canvas.click(position={"x": bounds["width"] * 0.5, "y": bounds["height"] * 0.80})
        expect(self.page.locator(".recommendation-contract")).to_contain_text("已涂抹约")
        expect(self.page.locator(".recommendation-contract")).to_contain_text("的掌面")
        self.page.get_by_role("button", name="查看匹配结果 →").click()
        expect(self.page.get_by_role("heading", name="1 款完全匹配 · 1 款相近匹配")).to_be_visible()
        expect(self.page.get_by_text("完全匹配", exact=True)).to_be_visible()
        expect(self.page.get_by_text("相近匹配", exact=True)).to_be_visible()
        expect(self.page.get_by_text("形状相似度 38%", exact=False)).to_be_visible()
        request = next(item for item in self.requests if item[:2] == ("POST", "/api/v1/mouse-recommendations"))
        payload = json.loads(request[2])
        self.assertGreater(len(payload["dabs"]), 0)
        self.assertNotIn("supportPositions", payload)

    def test_layout_adapts_without_scaling_typography_or_overflowing(self):
        home_widths = (375, 768, 1024, 1440, 1920, 2560, 3840)
        screenshot_dir = os.environ.get("E2E_RESPONSIVE_SCREENSHOT_DIR")

        for viewport_width in home_widths:
            with self.subTest(route="home", viewport_width=viewport_width):
                self.page.set_viewport_size({"width": viewport_width, "height": 900})
                self.page.goto(f"{BASE_URL}/")
                self.page.wait_for_load_state("networkidle")

                client_width = self.page.evaluate("document.documentElement.clientWidth")
                shell_width = self.page.locator(".home-page .section-shell").first.evaluate(
                    "element => element.getBoundingClientRect().width"
                )
                self.assertLessEqual(shell_width, client_width)
                self.assertGreaterEqual(shell_width, min(320, client_width - 24))
                self.assertEqual(
                    self.page.evaluate("getComputedStyle(document.documentElement).fontSize"),
                    "16px",
                )
                if viewport_width == 375:
                    scrollbar_style = self.page.evaluate("""() => ({
                        width: getComputedStyle(document.documentElement, '::-webkit-scrollbar').width,
                        buttonDisplay: getComputedStyle(document.documentElement, '::-webkit-scrollbar-button').display,
                    })""")
                    self.assertEqual(scrollbar_style["width"], "8px")
                    self.assertEqual(scrollbar_style["buttonDisplay"], "none")
                self.assertLessEqual(
                    self.page.evaluate("document.documentElement.scrollWidth"),
                    viewport_width,
                )

                if screenshot_dir and viewport_width in (375, 1440, 2560, 3840):
                    os.makedirs(screenshot_dir, exist_ok=True)
                    self.page.screenshot(
                        path=os.path.join(screenshot_dir, f"home-{viewport_width}.png"),
                        full_page=True,
                    )

        expected_catalog_columns = {1440: 3, 1920: 5, 2560: 5, 3840: 5}
        for viewport_width, expected_columns in expected_catalog_columns.items():
            with self.subTest(route="catalog", viewport_width=viewport_width):
                self.page.set_viewport_size({"width": viewport_width, "height": 900})
                self.page.goto(f"{BASE_URL}/mice")
                self.page.wait_for_load_state("networkidle")

                grid = self.page.locator(".database-results .mouse-grid")
                expect(grid).to_be_visible()
                columns = grid.evaluate(
                    "element => getComputedStyle(element).gridTemplateColumns.split(' ').length"
                )
                self.assertEqual(columns, expected_columns)
                self.assertLessEqual(
                    self.page.evaluate("document.documentElement.scrollWidth"),
                    viewport_width,
                )

                if screenshot_dir and viewport_width in (1440, 2560):
                    self.page.screenshot(
                        path=os.path.join(screenshot_dir, f"catalog-{viewport_width}.png"),
                        full_page=True,
                    )

    def test_advertising_rails_use_reserved_space_without_moving_content(self):
        self.public_config["advertisingEnabled"] = True
        self.page.set_viewport_size({"width": 1920, "height": 900})
        self.page.goto(f"{BASE_URL}/")
        self.page.wait_for_load_state("networkidle")

        shell_before = self.page.locator(".home-page .section-shell").first.evaluate(
            "element => ({ left: element.getBoundingClientRect().left, width: element.getBoundingClientRect().width })"
        )
        for side in ("left", "right"):
            rail = self.page.locator(f'[data-ad-slot="{side}-rail"]')
            expect(rail).to_be_visible()
            box = rail.bounding_box()
            self.assertAlmostEqual(box["width"], 220, delta=1)
            self.assertAlmostEqual(box["height"], 506, delta=1)
        self.assertLessEqual(self.page.evaluate("document.documentElement.scrollWidth"), 1920)

        self.public_config["advertisingEnabled"] = False
        self.page.reload()
        self.page.wait_for_load_state("networkidle")
        expect(self.page.locator('[data-ad-slot]')).to_have_count(0)
        shell_after = self.page.locator(".home-page .section-shell").first.evaluate(
            "element => ({ left: element.getBoundingClientRect().left, width: element.getBoundingClientRect().width })"
        )
        self.assertEqual(shell_after, shell_before)

        self.public_config["advertisingEnabled"] = True
        self.page.set_viewport_size({"width": 1024, "height": 900})
        self.page.reload()
        self.page.wait_for_load_state("networkidle")
        self.assertFalse(self.page.locator('[data-ad-slot="left-rail"]').is_visible())
        self.assertFalse(self.page.locator('[data-ad-slot="right-rail"]').is_visible())


if __name__ == "__main__":
    unittest.main()
