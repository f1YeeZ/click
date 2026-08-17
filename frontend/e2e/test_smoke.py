import base64
import json
import os
import re
import socket
import time
import unittest
from urllib.parse import parse_qs, urlparse

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
        "averageScore": 8.6,
        "reviewCount": 12,
        "lowReviewSample": False,
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
        "averageScore": 9.5,
        "reviewCount": 2,
        "lowReviewSample": True,
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
        self.review_saved = False
        self.grip_scores = []
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
        self.managed_user = {
            "id": "managed-user", "email": "managed@example.com", "role": "USER", "status": "ACTIVE",
            "handSize": "MEDIUM", "handLengthCm": 18.0, "preferredGripStyle": "CLAW",
            "statusReason": None, "statusChangedBy": None, "statusChangedAt": None,
            "createdAt": "2026-07-20T10:00:00+08:00", "updatedAt": "2026-07-20T10:00:00+08:00",
        }
        self.admin_review = {
            "id": "review-admin-a", "userId": "reviewer-a", "mouseId": "mouse-a",
            "userEmail": "reviewer@example.com", "mouseName": "Razer Viper V3 Pro",
            "status": "ACTIVE", "handSize": "MEDIUM", "comfortAverage": 8.5,
            "gripScores": [{"gripStyle": "CLAW", "comfortScore": 9}, {"gripStyle": "PALM", "comfortScore": 8}],
            "supportPositions": ["PALM_CENTER"], "supportCells": [],
            "supportDabs": [
                {"x": 460, "y": 560, "radius": 95, "mode": "PAINT"},
                {"x": 540, "y": 570, "radius": 80, "mode": "PAINT"},
            ],
            "moderationReason": None, "moderatedBy": None, "moderatedAt": None,
            "createdAt": "2026-07-26T17:30:00+08:00",
        }
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
                "mouse": MICE[0],
                "reviewSummary": {
                    "sampleCount": 8, "overallAverage": 8.4,
                    "dimensionAverages": {"comfort": 8.4}, "lowSample": False,
                    "scoreDistribution": {"10": 1, "9": 3, "8": 3, "7": 1, "6": 0, "5": 0, "4": 0, "3": 0, "2": 0, "1": 0},
                    "lastUpdatedAt": "2026-07-26T17:30:00+08:00",
                },
            }))
            return
        if path == "/api/v1/mice/mouse-a/review-summary" and method == "GET":
            count = len(self.grip_scores)
            route.fulfill(content_type="application/json", body=json.dumps({
                "sampleCount": count, "overallAverage": 8.0 if count else 0,
                "dimensionAverages": {"comfort": 8.0 if count else 0},
                "scoreDistribution": {"8": count}, "lowSample": True,
            }))
            return
        if path == "/api/v1/mice/mouse-a/support-summary" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps(self.support_summary))
            return
        if path == "/api/v1/mice/mouse-a/reviews/mine" and method == "GET":
            if not self.review_saved:
                route.fulfill(status=404, content_type="application/json", body='{"error":{"code":"REVIEW_NOT_FOUND","message":"尚未提交评价"}}')
                return
            route.fulfill(content_type="application/json", body=json.dumps({
                "id": "review-a", "mouseId": "mouse-a", "comfortAverage": 8.0,
                "gripComforts": self.grip_scores, "supportPositions": [], "supportCells": [],
                "supportByGrip": [
                    {"gripStyle": grip_style, "supportDabs": dabs, "supportCells": []}
                    for grip_style, dabs in self.personal_support_dabs_by_grip.items()
                    if dabs
                ],
            }))
            return
        if path == "/api/v1/mice/mouse-a/reviews" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({
                "items": [{"id": "public-review-a", "author": "re***@example.com", "gripStyle": "CLAW",
                    "handSize": "MEDIUM", "comfortAverage": 8.0,
                    "gripScores": [{"gripStyle": "CLAW", "comfortScore": 8}],
                    "createdAt": "2026-07-26T17:30:00+08:00"}],
                "page": {"number": 1, "totalPages": 1, "totalItems": 1},
            }))
            return
        if path.startswith("/api/v1/mice/mouse-a/reviews/mine/grip-scores/") and method == "PUT":
            grip_style = path.rsplit("/", 1)[-1]
            score = json.loads(route.request.post_data or "{}").get("comfortScore", 8)
            self.grip_scores.append({"gripStyle": grip_style, "comfortScore": score})
            self.review_saved = True
            route.fulfill(content_type="application/json", body='{}')
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
            route.fulfill(content_type="application/json", body=json.dumps({
                "items": [self.admin_review], "page": {"number": 1, "totalPages": 1, "totalItems": 1},
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
            route.fulfill(content_type="application/json", body=json.dumps({"items": [self.admin_report], "page": {"number": 1, "totalPages": 1, "totalItems": 1}}))
            return
        if path == "/api/v1/admin/reports/report-a" and method == "PATCH":
            self.admin_report.update(json.loads(route.request.post_data or "{}"))
            route.fulfill(content_type="application/json", body=json.dumps(self.admin_report))
            return
        if path == "/api/v1/admin/mice/imports" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({"items": [], "page": {"number": 1, "totalPages": 1, "totalItems": 0}}))
            return
        if path == "/api/v1/admin/sessions" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({"items": [], "page": {"number": 1, "totalPages": 1, "totalItems": 0}}))
            return
        if path == "/api/v1/admin/settings" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps([
                {"key": "registration.enabled", "value": "true", "description": "是否允许注册", "updatedBy": "system"},
            ]))
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
                "gripComfortAverage": 9.0, "gripComfortSampleCount": 3,
                "positionEvidence": {"PALM_CENTER": 3, "PALM_HEEL": 3}, "lowSample": True,
                "matchType": "EXACT", "supportCoveragePercent": 92, "shapeSimilarityPercent": 78,
                "explanation": "3 份同握姿评价达到图形匹配标准：期望范围覆盖 92%、形状相似度 78%。",
            }
            near = {
                "rank": 2, "mouse": MICE[1], "exactMatchCount": 0, "eligibleReviewCount": 5,
                "gripComfortAverage": 8.2, "gripComfortSampleCount": 2,
                "positionEvidence": {"PALM_CENTER": 2, "PALM_HEEL": 0}, "lowSample": True,
                "matchType": "NEAR", "supportCoveragePercent": 100, "shapeSimilarityPercent": 38,
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

    def test_catalog_exposes_reliable_rating_sort_and_sample_context(self):
        self.page.goto(f"{BASE_URL}/mice")
        self.page.wait_for_load_state("networkidle")
        self.page.get_by_label("排序方式").select_option("rating_desc")
        self.page.wait_for_url("**/mice?sort=rating_desc")
        expect(self.page.locator("article.mouse-card").first.get_by_text("8.6", exact=True)).to_be_visible()
        expect(self.page.get_by_text("样本较少", exact=True)).to_be_visible()

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
        expect(self.page.get_by_role("slider", name="长度最小值")).to_be_visible()
        self.assertLessEqual(self.page.evaluate("document.documentElement.scrollWidth"), 1440)

    def test_mobile_catalog_keeps_navigation_and_filters_touch_friendly(self):
        self.page.set_viewport_size({"width": 390, "height": 844})
        self.page.goto(f"{BASE_URL}/mice")
        self.page.wait_for_load_state("networkidle")

        mobile_nav = self.page.get_by_role("navigation", name="移动主导航")
        expect(mobile_nav).to_be_visible()
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

        metrics = self.page.evaluate("""() => ({
            viewport: document.documentElement.clientWidth,
            scrollWidth: document.documentElement.scrollWidth,
            navHeights: [...document.querySelectorAll('.mobile-nav a')].map((item) => item.getBoundingClientRect().height),
            inputFontSize: getComputedStyle(document.querySelector('.filter-search input')).fontSize,
        })""")
        self.assertEqual(metrics["scrollWidth"], metrics["viewport"])
        self.assertTrue(all(height >= 44 for height in metrics["navHeights"]))
        self.assertEqual(metrics["inputFontSize"], "16px")

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
        })""")
        self.assertEqual(nav_metrics["position"], "sticky")
        self.assertTrue(all(height >= 44 for height in nav_metrics["buttonHeights"]))
        screenshot_path = os.environ.get("E2E_ADMIN_MOBILE_SCREENSHOT")
        if screenshot_path:
            self.page.screenshot(path=screenshot_path, full_page=False)

    def test_z_detail_explains_score_distribution_and_update_time(self):
        self.page.goto(f"{BASE_URL}/mice/mouse-a")
        self.page.wait_for_load_state("networkidle")
        expect(self.page.get_by_role("heading", name="3D 手掌支撑热力图")).to_be_visible()
        expect(self.page.locator(".detail-hand-viewport .heatmap-score")).to_be_visible()
        expect(self.page.locator(".detail-hand-viewport .heatmap-filter-bar select")).to_have_count(2)

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
        expect(self.page.locator(".heatmap-score")).to_contain_text("抓握总评")

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
        self.page.get_by_role("link", name="首页", exact=True).click()
        self.page.wait_for_url(f"{BASE_URL}/")
        self.page.get_by_role("link", name="开始鼠标推荐", exact=True).click()
        self.page.wait_for_url(f"{BASE_URL}/recommend")

    def test_home_carousel_only_loops_with_more_than_four_mice(self):
        self.mice = [
            {**MICE[index % len(MICE)], "id": f"static-mouse-{index}"}
            for index in range(4)
        ]
        self.page.goto(f"{BASE_URL}/")

        cards = self.page.locator(".trending-track .mouse-card")
        expect(cards).to_have_count(4, timeout=5000)
        expect(self.page.locator(".trending-track .trending-set")).to_have_count(1)
        self.assertEqual(
            self.page.locator(".trending-track").evaluate(
                "element => getComputedStyle(element).animationName"
            ),
            "none",
        )

        self.mice = [
            {**MICE[index % len(MICE)], "id": f"looping-mouse-{index}"}
            for index in range(5)
        ]
        self.page.reload()

        expect(cards).to_have_count(10, timeout=5000)
        expect(self.page.locator(".trending-track .trending-set")).to_have_count(2)
        self.assertNotEqual(
            self.page.locator(".trending-track").evaluate(
                "element => getComputedStyle(element).animationName"
            ),
            "none",
        )

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
        self.page.goto(f"{BASE_URL}/")
        self.page.get_by_role("button", name="反馈").click()
        dialog = self.page.get_by_role("dialog", name="告诉我们哪里可以更好")
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

    def test_signed_in_user_can_submit_a_grip_comfort_review(self):
        user = {
            "id": "user-a", "email": "reviewer@example.com", "role": "USER",
            "handSize": "MEDIUM", "handLengthCm": 18.2, "preferredGripStyle": "CLAW",
        }
        self.allow_user_refresh = True
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.token', 'user-token'); sessionStorage.setItem('clicker.user', {json.dumps(json.dumps(user))});"
        )
        self.page.goto(f"{BASE_URL}/mice/mouse-a")
        self.page.get_by_role("button", name="写评价").click()
        dialog = self.page.get_by_role("dialog", name="评价 Viper V3 Pro")
        expect(dialog).to_be_visible()
        self.page.keyboard.press("Escape")
        expect(dialog).to_be_hidden()
        self.page.get_by_role("button", name="写评价").click()
        dialog.get_by_role("tab", name=re.compile("抓握")).click()
        dialog.get_by_label("抓握舒适度评分").press("ArrowRight")
        support_model = dialog.locator(".hand-support-3d")
        expect(support_model).to_have_class(re.compile(r"\bis-ready\b"), timeout=10000)
        support_canvas = support_model.locator('canvas[aria-label="可涂抹的抓握个人支撑位置图"]')
        bounds = support_canvas.bounding_box()
        self.assertIsNotNone(bounds)
        support_canvas.click(position={"x": bounds["width"] * 0.5, "y": bounds["height"] * 0.65})
        dialog.get_by_role("button", name="提交抓握完整评价").click()
        expect(dialog.get_by_text("抓握评分与支撑图已一并保存", exact=True)).to_be_visible()
        review_request = next(item for item in self.requests if item[:2] == ("PUT", "/api/v1/mice/mouse-a/reviews/mine/grip-scores/CLAW"))
        self.assertEqual(json.loads(review_request[2]), {"comfortScore": 9})
        support_request = next(item for item in self.requests if item[:2] == ("PUT", "/api/v1/mice/mouse-a/reviews/mine/support-positions/CLAW"))
        self.assertGreater(len(json.loads(support_request[2])["dabs"]), 0)

    def test_completed_grip_scores_remain_readable_on_mobile(self):
        user = {
            "id": "user-a", "email": "reviewer@example.com", "role": "USER",
            "handSize": "MEDIUM", "handLengthCm": 18.2, "preferredGripStyle": "CLAW",
        }
        self.allow_user_refresh = True
        self.review_saved = True
        self.grip_scores = [
            {"gripStyle": "PALM", "comfortScore": 7},
            {"gripStyle": "CLAW", "comfortScore": 8},
            {"gripStyle": "FINGERTIP", "comfortScore": 4},
            {"gripStyle": "MIXED", "comfortScore": 8},
        ]
        self.page.set_viewport_size({"width": 390, "height": 844})
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.token', 'user-token'); sessionStorage.setItem('clicker.user', {json.dumps(json.dumps(user))});"
        )
        self.page.goto(f"{BASE_URL}/mice/mouse-a")
        manage_review = self.page.get_by_role("button", name="管理我的评价")
        expect(manage_review).to_be_visible()
        manage_review_box = manage_review.bounding_box()
        self.assertIsNotNone(manage_review_box)
        self.assertLessEqual(manage_review_box["x"] + manage_review_box["width"], 390)
        manage_review.click()

        grip_tabs = self.page.locator(".support-grip-tabs button")
        expect(grip_tabs).to_have_count(4)
        for score in (7, 8, 4, 8):
            expect(self.page.locator(".support-grip-tabs")).to_contain_text(f"已评分 {score}")
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
        expect(self.page.locator(".detail-hand-viewport .heatmap-score")).to_be_visible()
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
        self.assertLessEqual(footer_alignment["topDelta"], 1)
        self.assertLessEqual(footer_alignment["bottomDelta"], 1)

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

        self.page.get_by_role("button", name="管理我的评价").click()
        expect(self.page.locator(".personal-support-editor")).to_be_visible()
        expect(self.page.locator(".hand-support-3d")).to_have_count(2)
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

    def test_personal_support_map_uses_left_button_for_painting_only(self):
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
        self.page.get_by_role("button", name="管理我的评价").click()

        model = self.page.locator(".personal-support-editor .hand-support-3d")
        expect(model).to_have_class(re.compile(r"\bis-ready\b"), timeout=10000)
        canvas = model.locator("canvas")
        canvas.scroll_into_view_if_needed()
        box = canvas.bounding_box()
        self.assertIsNotNone(box)
        center_x = box["x"] + box["width"] / 2
        center_y = box["y"] + box["height"] / 2

        canvas.evaluate("""() => {
            window.__supportRightPointerDefaultPrevented = null;
            document.addEventListener('pointerdown', event => {
                window.__supportRightPointerDefaultPrevented = event.defaultPrevented;
            }, { once: true });
        }""")
        self.page.mouse.move(center_x, center_y)
        self.page.mouse.down(button="right")
        self.assertEqual(canvas.evaluate("element => element.style.cursor"), "none")
        self.page.mouse.move(center_x + 24, center_y + 12, steps=4)
        self.page.mouse.up(button="right")
        self.page.wait_for_timeout(150)
        support_status = self.page.locator(".personal-support-editor .support-selection-status > strong")
        expect(support_status).to_have_text("尚未涂抹支撑区域")
        self.assertTrue(self.page.evaluate("window.__supportRightPointerDefaultPrevented"))

        self.page.get_by_role("button", name="旋转查看").click()
        before_left_rotation = canvas.screenshot()
        self.page.mouse.move(center_x, center_y)
        self.page.mouse.down(button="left")
        self.page.mouse.move(center_x - 24, center_y + 10, steps=4)
        self.page.mouse.up(button="left")
        self.page.wait_for_timeout(150)
        self.assertNotEqual(before_left_rotation, canvas.screenshot())
        self.page.get_by_role("button", name="涂抹", exact=True).click()

        self.page.mouse.move(center_x, center_y)
        self.page.mouse.down(button="middle")
        self.page.mouse.move(center_x, center_y + 16, steps=3)
        self.page.mouse.up(button="middle")
        expect(support_status).to_have_text("尚未涂抹支撑区域")

        self.page.mouse.click(center_x, center_y, button="left")
        expect(support_status).to_contain_text("已涂抹约")

    def test_admin_can_preview_and_commit_a_csv_import(self):
        admin = {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"}
        self.allow_admin_refresh = True
        self.page.add_init_script(
            f"sessionStorage.setItem('clicker.admin.token', 'admin-token'); sessionStorage.setItem('clicker.admin.user', {json.dumps(json.dumps(admin))});"
        )
        self.page.goto(f"{BASE_URL}/admin")
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
        self.page.get_by_role("button", name="评价治理").click()
        self.page.get_by_role("button", name="查看与处理").click()

        review_dialog = self.page.get_by_role("dialog", name="评价查看与处理")
        expect(review_dialog).to_be_visible()
        expect(review_dialog.get_by_text("支撑涂抹结果", exact=True)).to_be_visible()
        expect(review_dialog.locator(".review-detail-row")).to_have_count(0)
        expect(review_dialog.get_by_text("PALM_CENTER", exact=False)).to_have_count(0)
        expect(review_dialog.locator(".review-hand-model canvas")).to_be_visible()
        expect(review_dialog.locator(".hand-support-3d")).to_have_class(re.compile("is-ready"), timeout=15000)
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
        self.page.on("dialog", lambda dialog: dialog.accept())
        self.page.goto(f"{BASE_URL}/admin")
        self.page.get_by_role("button", name="用户管理").click()
        expect(self.page.get_by_text("managed@example.com", exact=True)).to_be_visible()
        self.page.get_by_role("button", name="管理用户").click()
        expect(self.page.get_by_role("dialog", name="管理用户")).to_be_visible()
        expect(self.page.locator(".user-management-modal")).to_be_visible()

        self.page.get_by_label("目标角色").select_option("ADMIN")
        self.page.get_by_label("调整原因").fill("负责鼠标数据维护")
        self.page.get_by_role("button", name="保存角色变更").click()
        expect(self.page.get_by_text("managed@example.com 已调整为管理员", exact=True)).to_be_visible()
        expect(self.page.get_by_text("管理员账号受保护；如需封禁", exact=False)).to_be_visible()

        self.page.get_by_label("目标角色").select_option("USER")
        self.page.get_by_label("调整原因").fill("结束临时数据维护")
        self.page.get_by_role("button", name="保存角色变更").click()
        expect(self.page.get_by_text("managed@example.com 已调整为普通用户", exact=True)).to_be_visible()

        screenshot_path = os.environ.get("E2E_USER_MANAGEMENT_SCREENSHOT")
        if screenshot_path:
            self.page.locator(".user-management-editor").screenshot(path=screenshot_path)

        self.page.get_by_label("处理原因").fill("异常登录行为")
        self.page.get_by_role("button", name="确认封禁用户").click()
        expect(self.page.locator("em.status-disabled")).to_have_text("已封禁")
        expect(self.page.get_by_text("异常登录行为", exact=True)).to_be_visible()
        role_requests = [item for item in self.requests if item[:2] == ("PATCH", "/api/v1/admin/users/managed-user/role")]
        self.assertEqual([json.loads(item[2])["role"] for item in role_requests], ["ADMIN", "USER"])
        ban_request = next(item for item in self.requests if item[:2] == ("PATCH", "/api/v1/admin/users/managed-user"))
        self.assertEqual(json.loads(ban_request[2]), {"status": "DISABLED", "reason": "异常登录行为"})

        self.page.get_by_role("button", name="管理用户").click()
        self.page.get_by_label("处理原因").fill("复核通过")
        self.page.get_by_role("button", name="确认解除封禁").click()
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
        expect(self.page.get_by_role("heading", name="举报与数据纠错")).to_be_visible()
        self.page.get_by_role("button", name=re.compile("系统运营")).click()
        expect(self.page.get_by_role("heading", name="系统运营", level=2)).to_be_visible()
        self.assertTrue(any(item[:2] == ("GET", "/api/v1/admin/analytics") for item in self.requests))
        self.assertTrue(any(item[:2] == ("GET", "/api/v1/admin/settings") for item in self.requests))

    def test_compare_selection_recovers_from_local_storage(self):
        selected = [{"id": mouse["id"], "displayName": mouse["displayName"]} for mouse in MICE]
        serialized = json.dumps(selected, ensure_ascii=False)
        self.page.add_init_script(
            f"localStorage.setItem('clicker.compare', {json.dumps(serialized)})"
        )
        self.page.goto(f"{BASE_URL}/compare")
        self.page.wait_for_load_state("networkidle")
        expect(self.page.locator(".selected-list .selected-mouse-row")).to_have_count(2)
        expect(self.page.locator(".comparison-table")).to_contain_text("+6g")
        self.assertEqual(parse_qs(urlparse(self.page.url).query).get("ids"), ["mouse-a,mouse-b"])

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
        self.page.goto(f"{BASE_URL}/recommend")
        self.page.locator(".recommendation-grips button").filter(has_text="抓握").click()
        support_canvas = self.page.locator('canvas[aria-label="可涂抹期望支撑位置的三维右手模型"]')
        expect(support_canvas).to_be_visible(timeout=10000)
        bounds = support_canvas.bounding_box()
        self.assertIsNotNone(bounds)
        support_canvas.click(position={"x": bounds["width"] * 0.5, "y": bounds["height"] * 0.61})
        support_canvas.click(position={"x": bounds["width"] * 0.5, "y": bounds["height"] * 0.80})
        expect(self.page.locator(".recommendation-contract")).to_contain_text("已涂抹约")
        expect(self.page.locator(".recommendation-contract")).to_contain_text("的掌面")
        self.page.get_by_role("button", name="查找匹配并解释原因 →").click()
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
                if viewport_width <= 430:
                    expected_shell_width = client_width - 24
                elif viewport_width <= 820:
                    expected_shell_width = client_width - 28
                elif viewport_width <= 1180:
                    expected_shell_width = viewport_width - 40
                elif viewport_width < 1440:
                    expected_shell_width = min(1280, viewport_width - 72)
                else:
                    expected_shell_width = min(
                        viewport_width - 96,
                        740 + (viewport_width * 0.375),
                        1760,
                    )
                shell_width = self.page.locator(".home-page .section-shell").first.evaluate(
                    "element => element.getBoundingClientRect().width"
                )
                self.assertAlmostEqual(shell_width, expected_shell_width, delta=1)
                self.assertEqual(
                    self.page.evaluate("getComputedStyle(document.documentElement).fontSize"),
                    "16px",
                )
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

        expected_catalog_columns = {1440: 3, 1920: 4, 2560: 5, 3840: 5}
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
