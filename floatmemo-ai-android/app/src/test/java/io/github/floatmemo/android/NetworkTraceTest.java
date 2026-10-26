package io.github.floatmemo.android;
import org.junit.Test;
import java.io.*;
import java.net.*;
import static org.junit.Assert.*;

public class NetworkTraceTest {
    @Test public void knownNetworkReasonsAreDistinguishedWithoutRawMessage(){assertEquals("CONNECTION_RESET",NetworkTrace.signature(new SocketException("recvfrom failed: ECONNRESET")));assertEquals("BROKEN_PIPE",NetworkTrace.signature(new SocketException("write failed: EPIPE")));assertEquals("SOCKET_CLOSED",NetworkTrace.signature(new SocketException("Socket closed")));assertEquals("UNEXPECTED_EOF",NetworkTrace.signature(new EOFException()));}
    @Test public void wrappedCausesAreInspected(){Exception e=new IOException("outer",new SocketTimeoutException("timeout"));assertEquals("SOCKET_TIMEOUT",NetworkTrace.signature(e));assertEquals("IOException → SocketTimeoutException",NetworkTrace.causes(e));}
    @Test public void arbitraryMessagesUrlsKeysAndNotesAreNotLogged(){Exception e=new SocketException("https://private.host/ secret_key private_note");assertEquals("UNSPECIFIED",NetworkTrace.signature(e));assertFalse(NetworkTrace.causes(e).contains("private"));}
    @Test public void settingStreamDoesNotChangeSavedTokenBudget(){RequestOptions a=new RequestOptions(18192,4096,120,10,12000,true),b=new RequestOptions(18192,4096,120,10,12000,false);assertEquals(18192,a.analyzeTokens);assertNotEquals(a.cacheKey(),b.cacheKey());}
}
