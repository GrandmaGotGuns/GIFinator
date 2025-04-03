import yt_dlp

def download_video(url, ffmpeg_path, download_path):
    try:
        ydl_opts = {
            'ffmpeg_location': ffmpeg_path,  # Point to the FFmpeg binary
            'outtmpl': f"{download_path}/%(title)s.%(ext)s",  # Save with title and extension
            'format': 'bestvideo+bestaudio/best',  # Download best quality video/audio
        }

        print(f"Downloading video from URL: {url}")
        print(f"Using FFmpeg binary at: {ffmpeg_path}")
        print(f"Saving to: {download_path}")

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            result = ydl.download([url])
            return "success" if result == 0 else "failure"

    except Exception as e:
        print(f"Error downloading video: {e}")
        return str(e)
