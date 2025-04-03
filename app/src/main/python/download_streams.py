import yt_dlp
import os

def download_content(itag, url, is_video, download_path):
    try:
        # Rename the file dynamically as 'video' or 'audio'
        file_prefix = "video" if is_video else "audio"

        # yt-dlp options
        ydl_opts = {
            'outtmpl': f"{download_path}/{file_prefix}.%(ext)s",  # Use 'video' or 'audio' as the file prefix
            'format': f"{itag}"  # Use the specified itag
        }

        print(f"Downloading {'video' if is_video else 'audio'} with itag: {itag}")
        print(f"URL: {url}")
        print(f"Saving to: {download_path}")

        # Download the file
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            result = ydl.download([url])
            return 100 if result == 0 else 0  # Success
    except Exception as e:
        print(f"Error during {'video' if is_video else 'audio'} download: {e}")
        return 0  # Failure
