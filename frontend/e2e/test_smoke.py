import json
import os
import re
import socket
import time
import unittest
from urllib.parse import parse_qs, urlparse

from playwright.sync_api import expect, sync_playwright


BASE_URL = os.environ.get("E2E_BASE_URL", "http://localhost:5173")

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
        self.review_saved = False
        self.grip_scores = []
        self.personal_support_dabs = []
        self.support_summary = {
            "sampleCount": 0, "positions": [], "cells": [], "maxCount": 0,
            "gridColumns": 64, "gridRows": 96,
        }
        self.import_committed = False
        self.managed_user = {
            "id": "managed-user", "email": "managed@example.com", "role": "USER", "status": "ACTIVE",
            "handSize": "MEDIUM", "handLengthCm": 18.0, "preferredGripStyle": "CLAW",
            "statusReason": None, "statusChangedBy": None, "statusChangedAt": None,
            "createdAt": "2026-07-20T10:00:00+08:00", "updatedAt": "2026-07-20T10:00:00+08:00",
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
        self.requests.append((method, path, route.request.post_data or ""))
        if path == "/api/v1/registration-verification-codes" and method == "POST":
            route.fulfill(
                status=201,
                content_type="application/json",
                body=json.dumps({"message": "验证码已发送", "expiresInSeconds": 600, "resendAfterSeconds": 60}, ensure_ascii=False),
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
                    "sampleCount": 12, "baseSampleCount": 12, "gripSampleCount": 8,
                    "baseAverage": 8.6, "gripAverage": 8.4,
                    "dimensionAverages": {"click": 9, "scroll": 8, "build": 9, "coating": 8.5},
                    "baseLowSample": False, "gripLowSample": False, "lowSample": False,
                    "baseScoreDistribution": {"10": 2, "9": 5, "8": 3, "7": 2, "6": 0, "5": 0, "4": 0, "3": 0, "2": 0, "1": 0},
                    "gripScoreDistribution": {"10": 1, "9": 3, "8": 3, "7": 1, "6": 0, "5": 0, "4": 0, "3": 0, "2": 0, "1": 0},
                    "lastUpdatedAt": "2026-07-26T17:30:00+08:00",
                },
            }))
            return
        if path == "/api/v1/mice/mouse-a/review-summary" and method == "GET":
            count = 1 if self.review_saved else 0
            route.fulfill(content_type="application/json", body=json.dumps({
                "sampleCount": count, "baseSampleCount": count, "gripSampleCount": 0,
                "baseAverage": 8.0 if count else 0, "gripAverage": 0,
                "dimensionAverages": {"click": 8, "scroll": 8, "build": 8, "coating": 8} if count else {},
                "baseLowSample": True, "gripLowSample": True, "lowSample": True,
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
                "id": "review-a", "mouseId": "mouse-a", "baseSubmitted": True,
                "clickScore": 8, "scrollScore": 8, "buildScore": 8, "coatingScore": 8,
                "gripComforts": self.grip_scores, "supportPositions": [], "supportCells": [],
                "supportDabs": self.personal_support_dabs,
            }))
            return
        if path == "/api/v1/mice/mouse-a/reviews/mine/base-score" and method == "PUT":
            self.review_saved = True
            route.fulfill(content_type="application/json", body='{}')
            return
        if path == "/api/v1/admin/dashboard" and method == "GET":
            route.fulfill(content_type="application/json", body=json.dumps({
                "miceTotal": 0, "micePublished": 0, "miceDraft": 0, "miceArchived": 0,
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
        if path == "/api/v1/mice/brands":
            route.fulfill(content_type="application/json", body=json.dumps(["Logitech", "Razer"]))
            return
        if path == "/api/v1/mice":
            query = parse_qs(urlparse(route.request.url).query).get("q", [""])[0].lower()
            items = [mouse for mouse in MICE if not query or query in mouse["model"].lower()]
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
                    "expiresInSeconds": 600,
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
        self.page.get_by_placeholder("按型号搜索").fill("Viper")
        self.page.wait_for_url("**/mice?q=Viper")
        expect(self.page.locator("article.mouse-card")).to_have_count(1)
        expect(self.page.get_by_text("Viper V3 Pro", exact=True)).to_be_visible()

    def test_catalog_exposes_reliable_rating_sort_and_sample_context(self):
        self.page.goto(f"{BASE_URL}/mice")
        self.page.wait_for_load_state("networkidle")
        self.page.locator(".filter-submitbar select").nth(1).select_option("rating_desc")
        self.page.wait_for_url("**/mice?sort=rating_desc")
        expect(self.page.locator("article.mouse-card").first.get_by_text("8.6", exact=True)).to_be_visible()
        expect(self.page.get_by_text("样本较少", exact=True)).to_be_visible()

    def test_z_detail_explains_score_distribution_and_update_time(self):
        self.page.goto(f"{BASE_URL}/mice/mouse-a")
        self.page.wait_for_load_state("networkidle")
        expect(self.page.get_by_role("heading", name="评分分布")).to_be_visible()
        method_note = self.page.get_by_text("口径说明：基础四项不受握姿筛选影响", exact=False)
        expect(method_note).to_be_hidden()
        expect(self.page.get_by_text("最后更新：", exact=False)).to_be_visible()
        self.page.locator("summary.score-distribution-toggle").click()
        expect(method_note).to_be_visible()

    def test_signed_in_user_sees_reviews_matching_their_hand_length_first(self):
        user = {
            "id": "user-a", "email": "reviewer@example.com", "role": "USER",
            "handSize": "MEDIUM", "handLengthCm": 18.2, "preferredGripStyle": "CLAW",
        }
        self.page.add_init_script(
            f"localStorage.setItem('clicker.token', 'user-token'); localStorage.setItem('clicker.user', {json.dumps(json.dumps(user))});"
        )

        self.page.goto(f"{BASE_URL}/mice/mouse-a")
        self.page.wait_for_load_state("networkidle")

        hand_filter = self.page.locator(".review-filters select").nth(1)
        expect(hand_filter).to_have_value("MEDIUM")
        match_context = self.page.locator(".review-match-context")
        expect(match_context.get_by_text("已优先展示匹配手长的评价", exact=True)).to_be_visible()
        expect(match_context.get_by_text("你的手长为 18.2 cm，对应 中手", exact=False)).to_be_visible()
        expect(self.page.locator(".support-filter-context strong")).to_have_text("全部握姿 · 中手")

        desktop_screenshot = os.environ.get("E2E_HAND_MATCH_DESKTOP_SCREENSHOT")
        if desktop_screenshot:
            self.page.locator(".review-panel").screenshot(path=desktop_screenshot)
        mobile_screenshot = os.environ.get("E2E_HAND_MATCH_MOBILE_SCREENSHOT")
        if mobile_screenshot:
            self.page.set_viewport_size({"width": 390, "height": 844})
            self.page.locator(".review-panel").screenshot(path=mobile_screenshot)

        match_context.get_by_role("button", name="查看全部").click()
        expect(hand_filter).to_have_value("")
        expect(match_context).to_be_hidden()
        expect(self.page.locator(".support-filter-context strong")).to_have_text("全部握姿 · 全部手长")

        self.page.locator(".breadcrumb").get_by_role("link", name="鼠标库", exact=True).click()
        self.page.wait_for_url(f"{BASE_URL}/mice")
        self.page.get_by_role("link", name="查看 Razer Viper V3 Pro 详情").click()
        self.page.wait_for_url(f"{BASE_URL}/mice/mouse-a")

        expect(self.page.locator(".review-filters select").nth(1)).to_have_value("MEDIUM")
        expect(self.page.locator(".review-match-context")).to_be_visible()
        self.assertFalse(any(path == "/api/v1/mice/undefined" for _, path, _ in self.requests))

    def test_home_search_suggests_mice_and_opens_the_selected_detail(self):
        self.page.goto(f"{BASE_URL}/")
        expect(self.page.locator(".quick-filters")).to_have_count(0)
        search_input = self.page.get_by_label("按型号搜索")
        expect(search_input).to_have_attribute("placeholder", "按型号搜索")
        search_input.fill("Viper")

        suggestions = self.page.get_by_role("listbox", name="鼠标搜索建议")
        expect(suggestions).to_be_visible()
        expect(suggestions.get_by_text("Viper V3 Pro", exact=True)).to_be_visible()
        self.assertEqual(self.page.url, f"{BASE_URL}/")

        desktop_screenshot = os.environ.get("E2E_HOME_SEARCH_DESKTOP_SCREENSHOT")
        if desktop_screenshot:
            self.page.locator(".home-hero").screenshot(path=desktop_screenshot)
        mobile_screenshot = os.environ.get("E2E_HOME_SEARCH_MOBILE_SCREENSHOT")
        if mobile_screenshot:
            self.page.set_viewport_size({"width": 390, "height": 844})
            self.page.locator(".home-hero").screenshot(path=mobile_screenshot)

        suggestions.get_by_role("option", name="查看 Razer Viper V3 Pro 详情").click()
        self.page.wait_for_url("**/mice/mouse-a")
        self.page.get_by_role("link", name="首页", exact=True).click()
        self.page.wait_for_url(f"{BASE_URL}/")
        expect(self.page.get_by_label("按型号搜索")).to_have_value("")

    def test_home_search_button_opens_the_filtered_catalog(self):
        self.page.goto(f"{BASE_URL}/")
        search_input = self.page.get_by_label("按型号搜索")
        search_input.fill("v")

        suggestions = self.page.get_by_role("listbox", name="鼠标搜索建议")
        option = suggestions.get_by_role("option", name="查看 Razer Viper V3 Pro 详情")
        expect(option).to_be_visible()
        option.hover()
        self.page.get_by_role("button", name="搜索", exact=True).click()

        self.page.wait_for_url("**/mice?q=v")
        expect(self.page.get_by_placeholder("按型号搜索")).to_have_value("v")
        expect(self.page.locator("article.mouse-card")).to_have_count(1)
        expect(self.page.get_by_text("Viper V3 Pro", exact=True)).to_be_visible()

        self.page.get_by_role("link", name="首页", exact=True).click()
        self.page.wait_for_url(f"{BASE_URL}/")
        expect(self.page.get_by_label("按型号搜索")).to_have_value("")

    def test_home_single_letter_search_filters_the_catalog_on_enter(self):
        self.page.goto(f"{BASE_URL}/mice")
        self.page.wait_for_load_state("networkidle")
        self.page.get_by_role("link", name="首页", exact=True).click()
        self.page.wait_for_url(f"{BASE_URL}/")
        search_input = self.page.get_by_label("按型号搜索")
        search_input.fill("v")
        search_input.press("Enter")

        self.page.wait_for_url("**/mice?q=v")
        catalog_search = self.page.get_by_placeholder("按型号搜索")
        expect(catalog_search).to_have_value("v")
        expect(self.page.locator("article.mouse-card")).to_have_count(1)
        expect(self.page.get_by_text("Viper V3 Pro", exact=True)).to_be_visible()

    def test_registration_creates_a_signed_in_user(self):
        self.page.goto(f"{BASE_URL}/register")
        self.page.get_by_label("邮箱", exact=True).fill("new-member@example.com")
        self.page.get_by_role("button", name="获取验证码").click()
        self.page.get_by_label("邮箱验证码").fill("123456")
        self.page.get_by_label("密码", exact=True).fill("password123")
        self.page.get_by_label("我已阅读并同意", exact=False).check()
        self.page.get_by_role("button", name="验证并创建账号 →").click()
        self.page.wait_for_url("**/mice")
        self.assertEqual(self.page.evaluate("localStorage.getItem('clicker.token')"), "user-token")
        registration_request = next(item for item in self.requests if item[:2] == ("POST", "/api/v1/users"))
        payload = json.loads(registration_request[2])
        self.assertEqual(payload["verificationCode"], "123456")
        self.assertTrue(payload["acceptedTerms"])

    def test_signed_in_user_can_submit_a_structured_review(self):
        user = {
            "id": "user-a", "email": "reviewer@example.com", "role": "USER",
            "handSize": "MEDIUM", "handLengthCm": 18.2, "preferredGripStyle": "CLAW",
        }
        self.page.add_init_script(
            f"localStorage.setItem('clicker.token', 'user-token'); localStorage.setItem('clicker.user', {json.dumps(json.dumps(user))});"
        )
        self.page.goto(f"{BASE_URL}/mice/mouse-a")
        self.page.get_by_role("button", name="写评价").click()
        dialog = self.page.get_by_role("dialog", name="评价 Viper V3 Pro")
        expect(dialog).to_be_visible()
        self.page.keyboard.press("Escape")
        expect(dialog).to_be_hidden()
        self.page.get_by_role("button", name="写评价").click()
        self.page.get_by_role("button", name="确认提交四项评分").click()
        expect(self.page.get_by_text("四项基础评分已提交", exact=True)).to_be_visible()
        review_request = next(item for item in self.requests if item[:2] == ("PUT", "/api/v1/mice/mouse-a/reviews/mine/base-score"))
        self.assertEqual(json.loads(review_request[2]), {
            "clickScore": 8, "scrollScore": 8, "buildScore": 8, "coatingScore": 8,
        })

    def test_completed_grip_scores_remain_readable_on_mobile(self):
        user = {
            "id": "user-a", "email": "reviewer@example.com", "role": "USER",
            "handSize": "MEDIUM", "handLengthCm": 18.2, "preferredGripStyle": "CLAW",
        }
        self.review_saved = True
        self.grip_scores = [
            {"gripStyle": "PALM", "comfortScore": 7},
            {"gripStyle": "CLAW", "comfortScore": 8},
            {"gripStyle": "FINGERTIP", "comfortScore": 4},
            {"gripStyle": "MIXED", "comfortScore": 8},
        ]
        self.page.set_viewport_size({"width": 390, "height": 844})
        self.page.add_init_script(
            f"localStorage.setItem('clicker.token', 'user-token'); localStorage.setItem('clicker.user', {json.dumps(json.dumps(user))});"
        )
        self.page.goto(f"{BASE_URL}/mice/mouse-a")
        manage_review = self.page.get_by_role("button", name="管理我的评价")
        manage_review_style = manage_review.evaluate(
            "element => { const style = getComputedStyle(element); return { background: style.backgroundColor, color: style.color, fontWeight: style.fontWeight }; }"
        )
        self.assertEqual(manage_review_style["background"], "rgb(29, 78, 216)")
        self.assertEqual(manage_review_style["color"], "rgb(255, 255, 255)")
        self.assertEqual(manage_review_style["fontWeight"], "700")
        manage_review.click()

        completed_cards = self.page.locator(".grip-score-list article.completed")
        expect(completed_cards).to_have_count(4)
        expect(self.page.get_by_text("✓ 已完成该握姿评分", exact=True)).to_have_count(4)

        delete_button = completed_cards.first.locator(".item-delete-button.compact")
        styles = delete_button.evaluate(
            "element => { const style = getComputedStyle(element); return { color: style.color, background: style.backgroundColor, fontSize: parseFloat(style.fontSize) }; }"
        )
        self.assertEqual(styles["color"], "rgb(229, 226, 227)")
        self.assertEqual(styles["background"], "rgb(11, 11, 12)")
        self.assertGreaterEqual(styles["fontSize"], 11.5)
        self.assertTrue(completed_cards.evaluate_all(
            "cards => cards.every(card => card.scrollWidth <= card.clientWidth)"
        ))
        screenshot_path = os.environ.get("E2E_READABILITY_SCREENSHOT")
        if screenshot_path:
            self.page.locator(".grip-score-list").screenshot(path=screenshot_path)

    def test_public_support_heatmap_is_separate_from_my_editable_map(self):
        user = {
            "id": "user-a", "email": "reviewer@example.com", "role": "USER",
            "handSize": "MEDIUM", "handLengthCm": 18.2, "preferredGripStyle": "CLAW",
        }
        self.review_saved = True
        self.personal_support_dabs = [{"x": 500, "y": 650, "radius": 70, "mode": "PAINT"}]
        self.support_summary = {
            "sampleCount": 12,
            "positions": [],
            "cells": [{"x": 31, "y": 62, "count": 8, "percentage": 67}],
            "maxCount": 8,
            "gridColumns": 64,
            "gridRows": 96,
        }
        self.page.add_init_script(
            f"localStorage.setItem('clicker.token', 'user-token'); localStorage.setItem('clicker.user', {json.dumps(json.dumps(user))});"
        )
        self.page.goto(f"{BASE_URL}/mice/mouse-a")

        expect(self.page.locator(".support-panel .public-support-map")).to_have_count(1)
        expect(self.page.locator(".support-panel .support-tools")).to_have_count(0)
        expect(self.page.locator('.public-support-map canvas[aria-label="所有用户支撑位置热力图"]')).to_be_visible(timeout=10000)

        self.page.get_by_role("button", name="管理我的评价").click()
        expect(self.page.locator(".personal-support-editor")).to_be_visible()
        expect(self.page.locator(".hand-support-3d")).to_have_count(2)
        expect(self.page.locator('.personal-support-editor canvas[aria-label="可涂抹的个人支撑位置图"]')).to_be_visible()
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
        self.review_saved = True
        self.page.add_init_script(
            f"localStorage.setItem('clicker.token', 'user-token'); localStorage.setItem('clicker.user', {json.dumps(json.dumps(user))});"
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
        expect(self.page.locator(".personal-support-editor > header > em")).to_have_text("尚未涂抹")
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
        expect(self.page.locator(".personal-support-editor > header > em")).to_have_text("尚未涂抹")

        self.page.mouse.click(center_x, center_y, button="left")
        expect(self.page.locator(".personal-support-editor > header > em")).to_contain_text("已涂抹约")

    def test_admin_can_preview_and_commit_a_csv_import(self):
        admin = {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"}
        self.page.add_init_script(
            f"localStorage.setItem('clicker.admin.token', 'admin-token'); localStorage.setItem('clicker.admin.user', {json.dumps(json.dumps(admin))});"
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
        expect(self.page.get_by_text("共 1 行，1 行通过", exact=True)).to_be_visible()
        expect(self.page.get_by_text("mice.csv", exact=True)).to_be_visible()
        self.page.get_by_role("button", name="确认写入数据库").click()
        expect(self.page.get_by_text("导入完成：新增 1 条，更新 0 条", exact=True)).to_be_visible()
        self.assertTrue(self.import_committed)
        self.assertTrue(any(item[:2] == ("POST", "/api/v1/admin/mice/imports/preview") for item in self.requests))
        self.assertTrue(any(item[:2] == ("POST", "/api/v1/admin/mice/imports") for item in self.requests))

    def test_admin_editor_shows_live_publication_checklist(self):
        admin = {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"}
        self.page.add_init_script(
            f"localStorage.setItem('clicker.admin.token', 'admin-token'); localStorage.setItem('clicker.admin.user', {json.dumps(json.dumps(admin))});"
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

    def test_admin_can_change_user_role_then_ban_the_account(self):
        admin = {"id": "admin-a", "email": "admin@example.com", "role": "ADMIN"}
        self.page.add_init_script(
            f"localStorage.setItem('clicker.admin.token', 'admin-token'); localStorage.setItem('clicker.admin.user', {json.dumps(json.dumps(admin))});"
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

    def test_compare_selection_recovers_from_local_storage(self):
        selected = [{"id": mouse["id"], "displayName": mouse["displayName"]} for mouse in MICE]
        serialized = json.dumps(selected, ensure_ascii=False)
        self.page.add_init_script(
            f"localStorage.setItem('clicker.compare', {json.dumps(serialized)})"
        )
        self.page.goto(f"{BASE_URL}/compare")
        self.page.wait_for_load_state("networkidle")
        expect(self.page.locator(".selected-list .selected-mouse-row")).to_have_count(2)
        expect(self.page.locator(".comparison-table")).to_contain_text("+11.1%")
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


if __name__ == "__main__":
    unittest.main()
