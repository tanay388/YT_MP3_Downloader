import yt_dlp
import sys
import json
from pathlib import Path


def extract_video_info(video_url):
    ydl_opts = {
        'ignoreerrors': True,
        'no_warnings': True,
    }

    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        try:
            info = ydl.extract_info(video_url, download=False)

            if info is None or 'title' not in info:
                print("Failed to extract video info")
                return None

            video_info = {
                'title': info.get('title', ''),
                'thumbnail': info.get('thumbnail', ''),
                'views': info.get('view_count', 0),
                'likes': info.get('like_count', 0),
                # Add more fields as needed
            }

            # Extract high-quality video media stream URL
            formats = info.get('formats', [])

            for fmt in formats:
                if fmt.get('format_note', '') == '720p':
                    video_info['media_stream'] = fmt.get('url', '')
                    break
                else:
                    print(fmt.get('format_note', ''))

            return video_info
        except yt_dlp.DownloadError as e:
            print(f"Failed to grab video info: {str(e)}")
            return None


if __name__ == '__main__':
    # Read video URL from command-line arguments
    video_url = sys.argv[1]
    result = extract_video_info(video_url)
    # Convert result to JSON and print
    print(json.dumps(result))
