package com.spw.animated.album.art;
public class ArtworkPlaylistTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  String base="https://mvod.itunes.apple.com/album/master.m3u8";
  String master="#EXTM3U\n#EXT-X-I-FRAME-STREAM-INF:CODECS=\"avc1\",RESOLUTION=768x768,URI=\"iframe.m3u8\"\n"
   +"#EXT-X-STREAM-INF:CODECS=\"avc1\",RESOLUTION=2160x2160\nhuge.m3u8\n"
   +"#EXT-X-STREAM-INF:CODECS=\"avc1\",RESOLUTION=768x768,VIDEO-RANGE=SDR\nnormal.m3u8\n"
   +"#EXT-X-STREAM-INF:CODECS=\"hvc1\",RESOLUTION=720x720\nhevc.m3u8\n";
  check(OnlineArtwork.variant(master,base).equals("https://mvod.itunes.apple.com/album/normal.m3u8"));
  String media="#EXTM3U\n#EXT-X-MAP:URI=\"clip.mp4\",BYTERANGE=\"100@0\"\n"
   +"#EXTINF:3,\n#EXT-X-BYTERANGE:200@100\nclip.mp4\n"
   +"#EXTINF:3,\n#EXT-X-BYTERANGE:300@300\nclip.mp4\n#EXT-X-ENDLIST\n";
  check(OnlineArtwork.singleFile(media,base).equals("https://mvod.itunes.apple.com/album/clip.mp4"));
  check(OnlineArtwork.singleFile(media.replace("300@300","300@301"),base)==null);
  check(OnlineArtwork.singleFile(media.replace("#EXT-X-ENDLIST",""),base)==null);
  check(OnlineArtwork.singleFile(media+"#EXT-X-KEY:METHOD=AES-128\n",base)==null);
  check(OnlineArtwork.singleFile(media.replace("clip.mp4","https://example.com/clip.mp4"),base)==null);
  check(OnlineArtwork.singleFile(media.replace("#EXT-X-BYTERANGE:300@300","#EXT-X-BYTERANGE:300"),base)!=null);
  System.out.println("PASS SDR variant choice, complete single-file ranges, implicit offsets, gap/encryption/untrusted-host rejection");
 }
}
