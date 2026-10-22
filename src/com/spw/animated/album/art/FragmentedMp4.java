package com.spw.animated.album.art;
import java.io.IOException;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
/** Rebase single-video Apple fMP4 fragments without invoking a platform muxer. */
public final class FragmentedMp4 {
 private record Box(String type,int payload,int end){}
 public static boolean rebase(Path input,Path output)throws IOException{
  long size=Files.size(input);if(size<16||size>104857600)return false;
  byte[] bytes=Files.readAllBytes(input);ByteBuffer data=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
  List<Box> boxes=new ArrayList<>();
  try{
   scan(data,0,bytes.length,boxes,0);
   if(count(boxes,"trak")!=1||count(boxes,"moov")!=1||count(boxes,"mdhd")!=1||count(boxes,"hdlr")!=1
      ||count(boxes,"edts")!=0||count(boxes,"sidx")!=0||count(boxes,"mfra")!=0)return false;
   Box handler=one(boxes,"hdlr");require(handler,12);
   if(data.getInt(handler.payload+8)!=0x76696465)return false; // vide
   for(String type:List.of("mvhd","mdhd","tkhd")){
    if(count(boxes,type)!=1)return false;
    Box box=one(boxes,type);require(box,4);int version=Byte.toUnsignedInt(bytes[box.payload]);
    if(version>1)return false;
    int offset=type.equals("tkhd")?(version==0?20:28):(version==0?16:24);
    require(box,offset+(version==0?4:8));
    long duration=version==0?Integer.toUnsignedLong(data.getInt(box.payload+offset)):data.getLong(box.payload+offset);
    // Nonzero explicit durations/edit lists need a general-purpose muxer.
    if(duration!=0)return false;
   }
   Box media=one(boxes,"mdhd");int version=Byte.toUnsignedInt(bytes[media.payload]);
   if(data.getInt(media.payload+(version==0?12:20))<=0)return false;
   Box track=one(boxes,"tkhd");int tv=Byte.toUnsignedInt(bytes[track.payload]);
   int trackId=data.getInt(track.payload+(tv==0?12:20));
   int fragments=count(boxes,"moof");
   if(fragments==0||count(boxes,"tfdt")!=fragments||count(boxes,"tfhd")!=fragments)return false;
   for(Box box:boxes)if(box.type.equals("tfhd")){require(box,8);if(data.getInt(box.payload+4)!=trackId)return false;}
   long origin=Long.MAX_VALUE;
   for(Box box:boxes)if(box.type.equals("tfdt"))origin=Math.min(origin,decodeTime(data,box));
   if(origin==Long.MAX_VALUE)return false;
   // Only tfdt values change. Box offsets, sample durations, composition offsets,
   // encoded video bytes and fragment lengths remain exactly as downloaded.
   for(Box box:boxes)if(box.type.equals("tfdt")){
    long shifted=decodeTime(data,box)-origin;
    if(bytes[box.payload]==1)data.putLong(box.payload+4,shifted);
    else data.putInt(box.payload+4,(int)shifted);
   }
  }catch(IllegalArgumentException|IndexOutOfBoundsException unsupported){return false;}
  Files.write(output,bytes);return true;
 }
 private static long decodeTime(ByteBuffer data,Box box){
  require(box,8);int version=Byte.toUnsignedInt(data.get(box.payload));
  if(version==0)return Integer.toUnsignedLong(data.getInt(box.payload+4));
  if(version!=1)throw new IllegalArgumentException("Unknown tfdt version");
  require(box,12);long value=data.getLong(box.payload+4);if(value<0)throw new IllegalArgumentException("Unsupported timestamp");return value;
 }
 private static void require(Box box,int size){if(box.end-box.payload<size)throw new IllegalArgumentException("Short MP4 box");}
 private static int count(List<Box> boxes,String type){return (int)boxes.stream().filter(b->b.type.equals(type)).count();}
 private static Box one(List<Box> boxes,String type){return boxes.stream().filter(b->b.type.equals(type)).findFirst().orElseThrow(()->new IllegalArgumentException("Missing box"));}
 private static void scan(ByteBuffer data,int start,int end,List<Box> result,int depth){
  if(depth>12)throw new IllegalArgumentException("Deep MP4");
  for(int at=start;at<end;){
   if(end-at<8)throw new IllegalArgumentException("Truncated box");
   long size=Integer.toUnsignedLong(data.getInt(at));int header=8;
   if(size==1){if(end-at<16)throw new IllegalArgumentException();size=data.getLong(at+8);header=16;}
   else if(size==0)size=end-at;
   if(size<header||size>end-at)throw new IllegalArgumentException("Invalid box size");
   byte[] name=new byte[4];for(int i=0;i<4;i++)name[i]=data.get(at+4+i);
   String type=new String(name,java.nio.charset.StandardCharsets.ISO_8859_1);
   Box box=new Box(type,at+header,at+(int)size);result.add(box);
   if(Set.of("moov","trak","mdia","moof","traf","edts").contains(type))scan(data,box.payload,box.end,result,depth+1);
   at=box.end;
  }
 }
}
