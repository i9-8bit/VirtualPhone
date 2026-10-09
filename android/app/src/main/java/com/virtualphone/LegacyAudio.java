package com.virtualphone;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LegacyAudio {
  public interface Sender { void send(byte[] pcm); }
  private static final int RATE=16000, CHANNEL=AudioFormat.CHANNEL_IN_MONO, OUT=AudioFormat.CHANNEL_OUT_MONO, FMT=AudioFormat.ENCODING_PCM_16BIT;
  private AudioRecord record; private AudioTrack track; private Thread tx,rx; private final AtomicBoolean running=new AtomicBoolean(false); private final Sender sender;
  public LegacyAudio(Sender s){sender=s;}
  public void start(){ if(!running.compareAndSet(false,true)) return; int minIn=AudioRecord.getMinBufferSize(RATE,CHANNEL,FMT), minOut=AudioTrack.getMinBufferSize(RATE,OUT,FMT); int bs=Math.max(2048,Math.max(minIn,minOut));
    record=new AudioRecord(MediaRecorder.AudioSource.MIC,RATE,CHANNEL,FMT,bs); track=new AudioTrack(AudioManager.STREAM_VOICE_CALL,RATE,OUT,FMT,bs,AudioTrack.MODE_STREAM); record.startRecording(); track.play();
    tx=new Thread(new Runnable(){public void run(){byte[] b=new byte[1024];while(running.get()){int n=record.read(b,0,b.length);if(n>0){byte[] x=new byte[n];System.arraycopy(b,0,x,0,n);sender.send(x);}}}}); tx.start(); }
  public void play(byte[] pcm){if(running.get()&&track!=null)track.write(pcm,0,pcm.length);}
  public void stop(){if(!running.compareAndSet(true,false))return;try{record.stop();}catch(Exception e){}try{track.stop();}catch(Exception e){}try{record.release();}catch(Exception e){}try{track.release();}catch(Exception e){}record=null;track=null;}
}
