package io.github.floatmemo.android;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Real loopback HTTP, synthetic data only. This does not validate Android or ModelScope. */
public class StreamingTransportTest {
    @Test public void incrementalReceptionCanOutlastPerReadTimeoutWithoutAnotherRequest()throws Exception{
        AtomicInteger requests=new AtomicInteger();AtomicReference<Throwable> failure=new AtomicReference<>();
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            Thread responder=new Thread(()->{try(Socket socket=server.accept()){
                BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String line;int length=0;
                while((line=in.readLine())!=null&&!line.isEmpty())if(line.toLowerCase().startsWith("content-length:"))length=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());
                char[] body=new char[length];int at=0,n;while(at<length&&(n=in.read(body,at,length-at))>0)at+=n;requests.incrementAndGet();assertEquals("{\"stream\":true}",new String(body));
                OutputStream out=socket.getOutputStream();out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));out.flush();
                for(int i=0;i<12;i++){out.write("data: {\"choices\":[{\"delta\":{\"content\":\"A\"},\"finish_reason\":null}]}\n\n".getBytes(StandardCharsets.UTF_8));out.flush();Thread.sleep(100);}
                out.write("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));out.flush();
            }catch(Throwable e){failure.set(e);}},"synthetic-stream-server");responder.setDaemon(true);responder.start();
            HttpURLConnection connection=(HttpURLConnection)new URL("http://127.0.0.1:"+server.getLocalPort()+"/").openConnection();connection.setRequestMethod("POST");connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(2000);connection.setReadTimeout(1000);connection.setDoOutput(true);byte[] request="{\"stream\":true}".getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(request.length);
            long start=System.nanoTime();try{try(OutputStream out=connection.getOutputStream()){out.write(request);}assertEquals(200,connection.getResponseCode());ResponseReader.Stats stats=new ResponseReader.Stats();String response=ResponseReader.read(connection.getInputStream(),connection.getContentType(),stats);assertEquals("A".repeat(12),ApiResponse.content(200,response));assertTrue(stats.events>1);assertTrue((System.nanoTime()-start)/1000000>=1000);assertEquals(1,requests.get());}finally{connection.disconnect();responder.join(2000);}
            if(failure.get()!=null)throw new AssertionError(failure.get());
        }
    }
}
