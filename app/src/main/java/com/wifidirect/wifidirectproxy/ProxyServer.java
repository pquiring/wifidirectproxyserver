package com.wifidirect.wifidirectproxy;

/**
 * Web Proxy Server
 *
 * @author pquiring
 *
 */

import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;
import java.security.*;

public class ProxyServer extends Thread {

    private ServerSocket ss;
    private static Vector<Session> list = new Vector<Session>();  //thread-safe
    private static int port = 8000;

    public static int getClientCount() {
        return list.size();
    }
    public static long lastAccess;

    public ProxyServer() {
        lastAccess = System.currentTimeMillis();
    }

    public void close() {
        android.util.Log.d("WDPS", "ProxyServer.close()");
        try {
            ss.close();
        } catch (Exception e) {}
        //close list
        Session sess;
        while (list.size() > 0) {
            sess = list.get(0);
            sess.close();
        }
    }

    public void run() {
        android.util.Log.d("WDPS", "ProxyServer.run() port=" + port);
        Socket s;
        Session sess;
        //try to bind to port 5 times (in case restart() takes a while)
        for(int a=0;a<5;a++) {
            try {
                ss = new ServerSocket(port);
            } catch (Exception e) {
                android.util.Log.d("WDPS", "ProxyServer.run() bind Exception=" + e);
                if (a == 4) return;
                try{Thread.sleep(1000);} catch (Exception e2) {}
                continue;
            }
            break;
        }
        android.util.Log.d("WDPS", "ProxyServer.run() main loop");
        try {
            while (!ss.isClosed()) {
                s = ss.accept();
                sess = new Session(s, false);
                sess.start();
            }
        } catch (Exception e) {
            android.util.Log.d("WDPS", "ProxyServer.run() main loop Exception=" + e);
            log(e.toString());
        }
    }

    private static int ba2int(byte ba[]) {
        int ret = 0;
        for(int a=0;a<4;a++) {
            ret <<= 8;
            ret += ((int)ba[a]) & 0xff;
        }
        return ret;
    }

    private static int atoi(String str) {
        return Integer.valueOf(str);
    }

    private static void log(String msg) {
        //TODO
    }

    public static byte[] readAll(InputStream in, int len) {
        try {
            byte ret[] = new byte[len];
            int pos = 0;
            while (pos < len) {
                int read = in.read(ret, pos, len - pos);
                if (read <= 0) {
                    return null;
                }
                pos += read;
            }
            return ret;
        } catch (Exception e) {
            log(e.toString());
            return null;
        }
    }

    private static int getIP(String ip) {
        String p[] = ip.split("[.]");
        byte o[] = new byte[4];
        for(int a=0;a<4;a++) {
            o[a] = (byte)atoi(p[a]);
        }
        return ba2int(o);
    }

    private int getMask(String mask) {
        int bits = atoi(mask);
        if (bits == 0) return 0;
        int ret = 0x80000000;
        bits--;
        while (bits > 0) {
            ret >>= 1;  //signed shift will repeat the sign bit (>>>=1 would not)
            bits--;
        }
        return ret;
    }

    private static class Session extends Thread {
        private Socket p, i;  //proxy, internet
        private InputStream pis, iis;
        private OutputStream pos, ios;
        private boolean disconn = false;
        private int client_port;
        private String client_ip;
        private boolean secure;
        public synchronized void close() {
            try {
                if ((p!=null) && (p.isConnected())) p.close();
                p = null;
            } catch (Exception e1) {}
            try {
                if ((i!=null) && (i.isConnected())) i.close();
                i = null;
            } catch (Exception e2) {}
            list.remove(this);
        }
        public Session(Socket s, boolean secure) {
            p = s;
            this.secure = secure;
        }
        public String toString(int ip) {
            long ip64 = ((long)ip) & 0xffffffffL;
            return Long.toString(ip64, 16);
        }
        private void log(String s) {
            android.util.Log.d("WDPS", "ProxyServer:" + client_ip + ":" + client_port + ":" + s);
        }
        private void log(Exception e) {
            String s = e.toString();
            StackTraceElement stack[] = e.getStackTrace();
            for(int a=0;a<stack.length;a++) {
                s += "\r\n" + stack[a].toString();
            }
            log(s);
        }
        public void run() {
            String req = "";
            int ch;
            list.add(this);
            lastAccess = System.currentTimeMillis();
            client_port = p.getPort();
            client_ip = p.getInetAddress().getHostAddress();
            log("Session Start");
            try {
                pis = p.getInputStream();
                pos = p.getOutputStream();
                while (true) {
                    req = "";
                    log("reading request");
                    do {
                        ch = pis.read();
                        if (ch == -1) throw new Exception("read error");
                        req += (char)ch;
                    } while (!req.endsWith("\r\n\r\n"));
                    lastAccess = System.currentTimeMillis();
                    proxy(req);
                    if (disconn) {
                        log("disconn");
                        break;
                    }
                }
                p.close();
            } catch (Exception e) {
                if (req.length() > 0) log(e);
            }
            close();
            log("Session Stop");
        }
        private int getIP(Socket s) {
            if (s.getInetAddress().isLoopbackAddress()) return 0x7f000001;  //loopback may return IP6 address
            byte o[] = s.getInetAddress().getAddress();
            return ba2int(o);
        }
        private void proxy(String req) throws Exception {
            String ln[] = req.split("\r\n");
            log("Proxy:" + ln[0]);
            int hostidx = -1;
            if (ln[0].endsWith("1.0")) disconn = true;  //HTTP/1.0
            for(int a=0;a<ln.length;a++) {
                if (ln[a].regionMatches(true, 0, "Host: ", 0, 6)) hostidx = a;
            }
            if (hostidx == -1) {
                log("ERROR : No host specified : " + req);
                replyError(505, "No host specified");
                return;
            }
            String hostln = ln[hostidx].substring(6);  //"Host: "
            String host;
            try {
                String method = null, proto = null, url = null, http = null;
                int port;
                String f[] = ln[0].split(" ");
                method = f[0];
                url = f[1];
                http = f[2];
                if (url.startsWith("http://")) {
                    proto = "http://";
                    url = url.substring(7);
                    port = 80;
                } else if (url.startsWith("ftp://")) {
                    proto = "ftp://";
                    url = url.substring(6);
                    port = 21;
                } else {
                    proto = "http://";  //assume http
                    port = secure ? 443 : 80;
                }
                int portidx = hostln.indexOf(':');
                if (portidx != -1) {
                    host = hostln.substring(0, portidx);
                    port = Integer.valueOf(hostln.substring(portidx+1));
                } else {
                    host = hostln;
                }
                if (method.equals("CONNECT")) {
                    connectCommand(host, ln[0]);
                    return;
                }
                if (proto.equals("http://")) {
                    connect(host, port);
                    sendRequest(ln);
                    if (method.equals("POST")) sendPost(ln);
                    relayReply(proto + url);
                }
                return;
            } catch (UnknownHostException uhe) {
                replyError(404, "Domain not found");
                log(uhe);
            } catch (IOException ioe) {
                /*do nothing*/
                log(ioe);
            } catch (Exception e) {
                replyError(505, "Exception:" + e);
                log(e);
            }
        }
        private void connect(String host, int port) throws UnknownHostException, IOException {
            log("connect:" + host + ":" + port);
            i = new Socket(host, port);
            iis = i.getInputStream();
            ios = i.getOutputStream();
        }
        private void replyError(int code, String msg) throws Exception {
            log("Error:" + code);
            String content = "<h1>Error : " + code + " : " + msg + "</h1>";
            String headers = "HTTP/1.1 " + code + " " + msg + "\r\nContent-Length: " + content.length() + "\r\n\r\n";
            pos.write(headers.getBytes());
            pos.write(content.getBytes());
            pos.flush();
        }
        private void sendRequest(String ln[]) throws Exception {
            String req = "";
            for(int a=0;a<ln.length;a++) {
                if (a == 0) ln[a] = removeHost(ln[a]);
                req += ln[a];
                req += "\r\n";
            }
            req += "\r\n";
            ios.write(req.getBytes());
            ios.flush();
        }
        private void sendPost(String ln[]) throws Exception {
            int length = -1;
            for(int a=0;a<ln.length;a++) {
                if (ln[a].regionMatches(true, 0, "Content-Length: ", 0, 16)) {
                    length = Integer.valueOf(ln[a].substring(16, ln[a].length()));
                }
            }
            if (length == -1) throw new Exception("unknown post size");
            log("sendPost data len=" + length);
            byte post[] = readAll(pis, length);
            ios.write(post);
            ios.flush();
        }
        private void relayReply(String fn) throws Exception {
            log("relayReply:" + fn);
            String tmp[];
            String line = "";
            String headers = "";
            int length = -1;
            int contentLength = -1;
            int ch;
            boolean first = true;
            int code;
            String encoding = "";
            do {
                ch = iis.read();
                if (ch == -1) throw new Exception("read error");
                line += (char)ch;
                if (!line.endsWith("\r\n")) continue;
                if (line.regionMatches(true, 0, "Content-Length: ", 0, 16)) {
                    length = Integer.valueOf(line.substring(16, line.length() - 2));
                    contentLength = length;
                }
                if (line.regionMatches(true, 0, "Connection: Close", 0, 17)) {
                    disconn = true;
                }
                if (line.regionMatches(true, 0, "Transfer-Encoding:", 0, 18)) {
                    encoding = line.substring(18).trim().toLowerCase();
                }
                if (first == true) {
                    //HTTP/1.1 CODE MSG
                    if (line.startsWith("HTTP/1.0")) disconn = true;
                    tmp = line.split(" ");
                    code = Integer.valueOf(tmp[1]);
                    log("reply=" + code + ":" + line);
                    first = false;
                }
                headers += line;
                if (line.length() == 2) break;  //blank line (double enter)
                line = "";
            } while (true);
            pos.write(headers.getBytes());
            pos.flush();
            if (length == 0) {
                log("reply:done:content.length=0:headers.length=" + headers.length());
                return;
            }
            if (length == -1) {
                if (encoding.equals("chunked")) {
                    //read chunked format
                    contentLength = 0;
                    while (true) {
                        //read chunk size followed by \r\n
                        String chunkSize = "";
                        while (true) {
                            ch = iis.read();
                            if (ch == -1) throw new Exception("read error");
                            chunkSize += (char)ch;
                            if (chunkSize.endsWith("\r\n")) break;
                        }
                        contentLength += chunkSize.length();
                        int idx = chunkSize.indexOf(";");  //ignore extensions
                        if (idx == -1) idx = chunkSize.length() - 2;
                        int chunkLength = Integer.valueOf(chunkSize.substring(0, idx), 16);
                        pos.write(chunkSize.getBytes());
                        boolean zero = chunkLength == 0;
                        //read chunk
                        chunkLength += 2;  // \r\n
                        contentLength += chunkLength;
                        int read , off = 0;
                        byte buf[] = new byte[chunkLength];
                        while (chunkLength != 0) {
                            read = iis.read(buf, off, chunkLength);
                            if (read == -1) throw new Exception("read error");
                            if (read > 0) {
                                chunkLength -= read;
                                off += read;
                            }
                        }
                        pos.write(buf);
                        pos.flush();
                        if (zero) break;
                    }
                } else {
                    contentLength = 0;
                    //read until disconnected (HTTP/1.0)
                    int read;
                    byte buf[] = new byte[64 * 1024];
                    while (true) {
                        read = iis.read(buf, 0, 64 * 1024);
                        if (read == -1) break;
                        if (read > 0) {
                            contentLength += read;
                            pos.write(buf, 0, read);
                            pos.flush();
                        }
                    }
                }
            } else {
                //read content (length known)
                int read, off = 0;
                byte buf[] = new byte[length];
                while (length != 0) {
                    read = iis.read(buf, off, length);
                    if (read == -1) break;
                    if (read > 0) {
                        length -= read;
                        off += read;
                    }
                }
                pos.write(buf);
                pos.flush();
            }
            log("reply:done:content.length=" + contentLength + ":headers.length=" + headers.length());
        }
        private void connectCommand(String host, String req) throws Exception {
            String ln[] = req.split(" ");
            if (ln.length != 3) {
                replyError(505, "Bad CONNECT syntax");
                return;
            }
            int portidx = ln[1].indexOf(':');
            if (portidx != -1) {
                int port = Integer.valueOf(ln[1].substring(portidx+1));
                if (port != 443) {
                    replyError(505, "CONNECT is for port 443 only");
                    return;
                }
            }
            connect(host, 443);
            pos.write("HTTP/1.1 200 OK\r\n\r\n".getBytes());
            pos.flush();
            ConnectRelay i2p = new ConnectRelay(iis, pos);
            ConnectRelay p2i = new ConnectRelay(pis, ios);
            i2p.start();
            p2i.start();
            i2p.join();
            p2i.join();
            disconn = true;  //not HTTP/1.1 compatible?
        }
        private String removeHost(String req) throws Exception {
            //GET URL HTTP/1.1
            //remove host from URL if present
            String p[] = req.split(" ");
            if (p.length != 3) return req;
            String urlstr = p[1];
            if ((!urlstr.startsWith("http:")) && (!urlstr.startsWith("https:"))) return req;
            URL url = new URL(urlstr);
            return p[0] + " " + url.getFile() + " " + p[2];
        }
        private class ConnectRelay extends Thread {
            private InputStream is;
            private OutputStream os;
            private byte buf[] = new byte[4096];
            private final int buflen = 4096;
            public ConnectRelay(InputStream is, OutputStream os) {
                this.is = is;
                this.os = os;
            }
            public void run() {
                int read;
                try {
                    while (true) {
                        read = is.read(buf, 0, buflen);
                        if (read == -1) break;
                        if (read > 0) {os.write(buf, 0, read); os.flush();}
                    }
                } catch (Exception e) {}
            }
        }
    }
}
