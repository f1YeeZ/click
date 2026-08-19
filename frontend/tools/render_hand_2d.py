from pathlib import Path

from PIL import Image
from playwright.sync_api import sync_playwright


ROOT = Path(__file__).resolve().parents[1]
RAW_PATH = ROOT / "hand-palm-model-render.png"
OUTPUT_PATH = ROOT / "src/assets/images/hand-palm-model-projection.png"


def main():
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True, channel="chromium")
        page = browser.new_page(viewport={"width": 1200, "height": 1400}, device_scale_factor=1)
        page.goto("http://127.0.0.1:5173/recommend", wait_until="domcontentloaded")
        model = page.locator(".recommendation-model-stage .hand-support-3d")
        model.wait_for(state="visible", timeout=15000)
        page.wait_for_function(
            "() => document.querySelector('.recommendation-model-stage .hand-support-3d')?.classList.contains('is-ready')",
            timeout=15000,
        )
        page.add_style_tag(content="""
          html, body, #app, .recommendation-page, .recommendation-lab,
          .recommendation-hand-panel, .recommendation-hand-map,
          .recommendation-model-stage { background: transparent !important; }
          .recommendation-model-stage::before,
          .recommendation-model-hint,
          .feedback-fab,
          .compare-tray,
          .mobile-bottom-nav { display: none !important; }
          .hand-support-3d canvas { filter: none !important; }
        """)
        stage = page.locator(".recommendation-model-stage")
        stage.evaluate("element => { element.style.width = '900px'; element.style.height = '1200px'; element.style.aspectRatio = 'auto'; }")
        page.wait_for_timeout(350)
        canvas = model.locator("canvas")
        canvas.screenshot(path=str(RAW_PATH), omit_background=True)
        browser.close()

    image = Image.open(RAW_PATH).convert("RGBA").resize((900, 1200), Image.Resampling.LANCZOS)
    alpha = image.getchannel("A")
    if alpha.getbbox() is None:
        raise RuntimeError("3D model render contains no visible pixels")
    image.save(OUTPUT_PATH)
    RAW_PATH.unlink(missing_ok=True)
    print(f"saved {OUTPUT_PATH} ({image.width}x{image.height})")


if __name__ == "__main__":
    main()
