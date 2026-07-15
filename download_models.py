import os
import json
import urllib.request
import urllib.parse

# GitHub Release URL and API URL
RELEASE_TAG = "1.0.0"
REPO = "yumu908/Vits-Android-ncnn"
API_URL = f"https://api.github.com/repos/{REPO}/releases/tags/{RELEASE_TAG}"
FALLBACK_BASE_URL = f"https://github.com/{REPO}/releases/download/{RELEASE_TAG}"

# Static fallback list of zip files in case the API rate limit is exceeded
STATIC_ZIP_FILES = [
    "anan.zip", "arisa.zip", "ema.zip", "gokucho.zip", "hanna.zip",
    "hiro.zip", "koko.zip", "mago.zip", "meruru.zip", "miria.zip",
    "nanoka.zip", "noa.zip", "reia.zip", "sheri.zip", "yuki.zip"
]

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
}

print(f"正在从 GitHub API 获取 release {RELEASE_TAG} 的文件列表...")

zip_files = []

# Try to fetch dynamically from GitHub API
req = urllib.request.Request(API_URL, headers=headers)
try:
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read().decode('utf-8'))
        assets = data.get("assets", [])
        for asset in assets:
            name = asset.get("name", "")
            download_url = asset.get("browser_download_url", "")
            if name.endswith(".zip") and download_url:
                zip_files.append((download_url, name))
except Exception as e:
    print(f"通过 API 获取文件列表失败 ({e})，正在使用内置静态列表进行下载...")
    for filename in STATIC_ZIP_FILES:
        download_url = f"{FALLBACK_BASE_URL}/{filename}"
        zip_files.append((download_url, filename))

if not zip_files:
    print("未找到任何 .zip 文件。")
    exit(0)

print(f"找到 {len(zip_files)} 个模型压缩包:")
for _, name in zip_files:
    print(f" - {name}")

# 创建存放目录
output_dir = "downloaded_models"
os.makedirs(output_dir, exist_ok=True)

# 依次下载
for download_url, name in zip_files:
    dest_path = os.path.join(output_dir, name)
    print(f"\n正在下载 {name}...")

    get_req = urllib.request.Request(download_url, headers=headers)

    try:
        with urllib.request.urlopen(get_req) as response:
            file_size = int(response.headers.get("Content-Length", 0))
            downloaded = 0
            block_size = 1024 * 64

            with open(dest_path, "wb") as f:
                while True:
                    buffer = response.read(block_size)
                    if not buffer:
                        break
                    f.write(buffer)
                    downloaded += len(buffer)
                    if file_size > 0:
                        percent = (downloaded / file_size) * 100
                        print(
                            f"\r进度: {percent:.1f}% ({downloaded}/{file_size} 字节)",
                            end="",
                            flush=True,
                        )
                    else:
                        print(f"\r已下载 {downloaded} 字节", end="", flush=True)
            print(f"\n成功下载 {name} 到 {dest_path}")
    except Exception as e:
        print(f"\n下载 {name} 失败: {e}")

print("\n所有文件下载完成！")
