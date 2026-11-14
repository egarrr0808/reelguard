#!/usr/bin/env python3
"""Exercise production Android helper on JVM with persistence and model fixtures."""
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT/'work/helper-test'
sys.path.insert(0, str(ROOT.parent / 'scripts'))
from environment import JDK as JDK_HOME
JDK=JDK_HOME/'bin'
sources={
'android/content/Context.java':'''package android.content; public abstract class Context {public Context getApplicationContext(){return this;} public abstract SharedPreferences getSharedPreferences(String n,int m);}''',
'android/content/SharedPreferences.java':'''package android.content; import java.util.Set; public interface SharedPreferences {long getLong(String k,long d); Set<String> getStringSet(String k,Set<String>d); Editor edit(); interface Editor {Editor putInt(String k,int v);Editor putLong(String k,long v);Editor putStringSet(String k,Set<String>v);boolean commit();}}''',
'android/util/Log.java':'''package android.util;public class Log {public static int i(String t,String s){return 0;}public static int e(String t,String s){return 0;}}''',
'X/Z7BI.java':'''package X;public class Z7BI {public boolean A08;public Rows A04;public Rows A00;}''',
'X/Z6qW.java':'''package X;public class Z6qW extends Z7BI {}''',
'X/Rows.java':'''package X;public class Rows {public java.util.List<Row> A02=new java.util.ArrayList<>();public java.util.List<Row> A06=A02;}''',
'X/Row.java':'''package X;public class Row {public Object A05;public Object A02;public Row(Object item){A05=item;A02=item;}}''',
'X/Z6qX.java':'''package X; public class Z6qX {}''',
'X/Z0PG.java':'''package X;public class Z0PG {private com.instagram.feed.media.Media media;public Z0PG(String id){media=id==null?null:new com.instagram.feed.media.Media(id);}public com.instagram.feed.media.Media A07(){return media;}}''',
'com/instagram/feed/media/Media.java':'''package com.instagram.feed.media;public class Media {private String id;public Media(String id){this.id=id;}public String getId(){return id;}}''',
'HelperTest.java':r'''import local.reelguard.quota.HomeQuota;
import android.content.*;import java.util.*;import java.lang.reflect.*;
public class HelperTest {
 static class Prefs implements SharedPreferences {
  Map<String,Object> data=new HashMap<>();boolean writable=true;
  public long getLong(String k,long d){return data.containsKey(k)?(Long)data.get(k):d;}
  public Set<String> getStringSet(String k,Set<String>d){return data.containsKey(k)?new HashSet<>((Set<String>)data.get(k)):d;}
  public Editor edit(){return new Editor(){Map<String,Object> changes=new HashMap<>();public Editor putInt(String k,int v){changes.put(k,v);return this;}public Editor putLong(String k,long v){changes.put(k,v);return this;}public Editor putStringSet(String k,Set<String>v){changes.put(k,new HashSet<>(v));return this;}public boolean commit(){if(!writable)return false;data.putAll(changes);return true;}};}
 }
 static class Ctx extends Context {Prefs p;Ctx(Prefs p){this.p=p;}public SharedPreferences getSharedPreferences(String n,int m){return p;}}
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 static void reset()throws Exception{for(String n:new String[]{"prefs","state","mediaMethod","idMethod"}){Field f=HomeQuota.class.getDeclaredField(n);f.setAccessible(true);f.set(null,null);}Field f=HomeQuota.class.getDeclaredField("failed");f.setAccessible(true);f.setBoolean(null,false);}
 static Object item(String id)throws Exception{return Class.forName("X.00PG").getConstructor(String.class).newInstance(id);}
 public static void main(String[] args)throws Exception{
  Object home=Class.forName("X.06qX").getConstructor().newInstance();Prefs p=new Prefs();
  check(!HomeQuota.allow(home,item("a")),"uninitialized Home closed");
  check(HomeQuota.allow(new Object(),new Object()),"other models untouched");
  HomeQuota.attach(new Ctx(p));
  List<Object> batch=new ArrayList<>();for(int i=0;i<100;i++)batch.add(item("post"+i));
  List<?> visible=HomeQuota.filter(home,batch);check(visible.size()==20,"batch limited before display");
  check(HomeQuota.filter(home,batch).size()==20,"refresh limited");
  check(!HomeQuota.allow(home,item("post100")),"raw insertion limited");
  check(!HomeQuota.allow(home,item(null)),"nested/unknown container denied");
  check(HomeQuota.filter(new Object(),batch)==batch,"other feeds exact identity");
  Object adapter=Class.forName("X.06qW").getConstructor().newInstance();
  Class<?> base=Class.forName("X.07BI");X.Rows rows=new X.Rows();
  rows.A02.add(new X.Row(new com.instagram.feed.media.Media("post0")));
  rows.A02.add(new X.Row(new com.instagram.feed.media.Media("post100")));
  base.getField("A04").set(adapter,rows);base.getField("A08").setBoolean(adapter,true);
  check(HomeQuota.capRows(adapter,2)==1,"refreshed flattened row cache denied at first new post");
  base.getField("A00").set(adapter,rows);base.getField("A08").setBoolean(adapter,false);
  check(HomeQuota.capRows(adapter,2)==1,"legacy row cache denied at first new post");
  rows.A02.clear();rows.A02.add(new X.Row(new com.instagram.feed.media.Media("post100")));
  check(HomeQuota.capRows(adapter,1)==0,"entirely new refresh empty");
  check(HomeQuota.capRows(new Object(),25)==25,"other adapter count unchanged");
  reset();HomeQuota.attach(new Ctx(p));check(HomeQuota.filter(home,batch).size()==20,"process restart");
  check(!HomeQuota.allow(home,item("post100")),"restart no extra admission");
  p.data.put("day",29991231L);reset();HomeQuota.attach(new Ctx(p));check(!HomeQuota.allow(home,item("new")),"clock rollback");
  p.data.put("day",20200101L);reset();HomeQuota.attach(new Ctx(p));check(HomeQuota.allow(home,item("next-day")),"calendar rollover");
  check(p.getStringSet("post_hashes",null).size()==1,"rollover persisted");
  p.writable=false;check(!HomeQuota.allow(home,item("disk-full")),"failed write denied");
  check(!HomeQuota.allow(home,item("next-day")),"persistence failure closes Home");
  p=new Prefs();reset();HomeQuota.attach(new Ctx(p));
  final Object model=home;List<Thread> threads=new ArrayList<>();
  for(int i=0;i<100;i++){final Object post=item("concurrent"+i);Thread t=new Thread(()->HomeQuota.allow(model,post));threads.add(t);t.start();}
  for(Thread t:threads)t.join();check(p.getStringSet("post_hashes",null).size()==20,"concurrent hard cap");
  System.out.println("PASS production helper: batch, refresh, restart, Home isolation, rollover, rollback, persistence failure, 100 concurrent admissions");
 }
}'''
}
WORK.mkdir(parents=True,exist_ok=True)
files=[]
for name,source in sources.items():
    path=WORK/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source);files.append(path)
classes=WORK/'classes'
subprocess.run([str(JDK/'javac'),'-d',str(classes),*[str(p)for p in files],*[str(p)for p in(ROOT/'src').rglob('*.java')]],check=True)
# DEX permits numeric obfuscated class names. Rename fixture class constants to match real target.
for p in classes.rglob('*.class'):
    data=p.read_bytes().replace(b'X/Z6qX',b'X/06qX').replace(b'X/Z0PG',b'X/00PG').replace(b'X/Z7BI',b'X/07BI').replace(b'X/Z6qW',b'X/06qW')
    p.write_bytes(data)
for old,new in [('Z6qX','06qX'),('Z0PG','00PG'),('Z7BI','07BI'),('Z6qW','06qW')]:
    (classes/'X'/f'{old}.class').rename(classes/'X'/f'{new}.class')
subprocess.run([str(JDK/'java'),'-cp',str(classes),'HelperTest'],check=True)
