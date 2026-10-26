package io.github.floatmemo.android;

import java.util.*;

/** Whitelisted network signatures only: never persist arbitrary exception messages. */
public final class NetworkTrace {
    public static String signature(Throwable error){
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());int depth=0;
        while(error!=null&&seen.add(error)&&depth++<5){String msg=error.getMessage()==null?"":error.getMessage().toLowerCase(Locale.ROOT);
            if(msg.contains("connection reset")||msg.contains("econnreset"))return "CONNECTION_RESET";
            if(msg.contains("broken pipe")||msg.contains("epipe"))return "BROKEN_PIPE";
            if(msg.contains("socket closed")||msg.contains("socket is closed"))return "SOCKET_CLOSED";
            if(msg.contains("connection abort")||msg.contains("econnaborted"))return "CONNECTION_ABORTED";
            if(msg.contains("unexpected end")||error instanceof java.io.EOFException)return "UNEXPECTED_EOF";
            if(error instanceof java.net.SocketTimeoutException)return "SOCKET_TIMEOUT";
            error=error.getCause();
        }return "UNSPECIFIED";
    }
    public static String causes(Throwable error){Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());List<String> types=new ArrayList<>();while(error!=null&&seen.add(error)&&types.size()<5){types.add(error.getClass().getSimpleName());error=error.getCause();}return String.join(" → ",types);}
}
