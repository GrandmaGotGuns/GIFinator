import yt_dlp

def get_best_streams(url):
    """
    Fetch and return the best itag for each video resolution and audio bitrate
    in the requested output format.
    """
    video_streams = {}
    audio_streams = {}

    try:
        ydl_opts = {
            'quiet': False,  # Suppress verbose output
            'extract_flat': False,  # Ensure full info is extracted
        }

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info_dict = ydl.extract_info(url, download=False)
            formats = info_dict.get('formats', [])

            # Iterate through all available streams
            for stream in formats:
                if stream.get('vcodec') != 'none':  # Handle video streams
                    resolution = stream.get('height')  # Video resolution
                    itag = stream.get('format_id')  # Format ID (itag)
                    filesize = stream.get('filesize', 0)  # Filesize (default to 0 if not provided)

                    # Ensure resolution and itag are valid
                    if resolution is not None and itag:
                        # Only keep the stream with the largest file size for each resolution
                        if resolution not in video_streams or (filesize is not None and filesize > video_streams[resolution].get('filesize', 0)):
                            video_streams[resolution] = {
                                'itag': itag,
                                'filesize': filesize
                            }

                elif stream.get('acodec') != 'none':  # Handle audio streams
                    bitrate = stream.get('tbr')  # Audio bitrate (in kbps)
                    itag = stream.get('format_id')  # Format ID (itag)
                    filesize = stream.get('filesize', 0)  # Filesize (default to 0 if not provided)

                    # Ensure bitrate and itag are valid
                    if bitrate is not None and itag:
                        # Only keep the stream with the largest file size for each bitrate
                        if bitrate not in audio_streams or (filesize is not None and filesize > audio_streams[bitrate].get('filesize', 0)):
                            audio_streams[bitrate] = {
                                'itag': itag,
                                'filesize': filesize
                            }

            # Prepare formatted output
            video_list = [
                f"{res}p (itag={data['itag']}, Size={data['filesize'] / (1024 * 1024):.2f} MB)" if data['filesize']
                else f"{res}p (itag={data['itag']})"
                for res, data in sorted(video_streams.items(), reverse=True)
            ]
            audio_list = [
                f"{bitrate} kbps (itag={data['itag']}, Size={data['filesize'] / (1024 * 1024):.2f} MB)" if data['filesize']
                else f"{bitrate} kbps (itag={data['itag']})"
                for bitrate, data in sorted(audio_streams.items(), reverse=True)
            ]

            resolutions_text = ", ".join(video_list)
            bitrates_text = ", ".join(audio_list)

            return f"Available Resolutions (Video): {resolutions_text}\nAvailable Bitrates (Audio): {bitrates_text}"

    except Exception as e:
        return f"Error: {e}"


