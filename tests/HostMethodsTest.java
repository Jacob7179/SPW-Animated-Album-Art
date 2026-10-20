package com.spw.animated.album.art;
import java.util.List;
public class HostMethodsTest {
 static class Windows { private long a(){return 1;} }
 static class Linux { private long renamed(){return 2;} }
 static class Ambiguous { long a(){return 1;} long b(){return 2;} }
 static class State { private int renamed=7; int a(){return 7;} int b(){return 7;} }
 static class Generic { List<State> renamed(){return null;} List<String> other(){return null;} }
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args)throws Exception {
  check((long)HostMethods.method(Windows.class,long.class,false).invoke(new Windows())==1);
  check((long)HostMethods.method(Linux.class,long.class,false).invoke(new Linux())==2);
  for(Class<?> type:new Class<?>[]{Ambiguous.class,State.class}){
   try{HostMethods.method(type,long.class,false);throw new AssertionError("Nonunique/missing accepted");}
   catch(IllegalStateException expected){}
  }
  check(HostMethods.instanceField(State.class,int.class).getInt(new State())==7);
  check(HostMethods.genericGetter(Generic.class,List.class,State.class).getReturnType()==List.class);
  var targets=HostMethods.resolve();
  check(targets.readImage()!=null&&targets.makeShader()!=null&&targets.painterDraw()!=null);
  check(HostMethods.instanceField(Class.forName("androidx.compose.ui.draw.PainterElement"),androidx.compose.ui.graphics.painter.Painter.class)!=null);
  System.out.println("PASS renamed member fixtures, ambiguity rejection, generic/field shapes and actual Windows host: "+targets);
 }
}
