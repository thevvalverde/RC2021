import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.Map.Entry;

import javax.swing.text.Keymap;

class SocData {
  private String nick;
  private int status; // 0 = init | 1 = outside | 2 = inside
  private String room;
  public StringBuffer sb;

  public SocData() {
    this.nick = "";
    this.status = 0;
    this.room = "";
    sb = new StringBuffer(16384);
  }

  public SocData(String nick, int status, String room) {
    this.nick = nick;
    this.status = status;
    this.room = room;
    sb = new StringBuffer(16384);
  }

  public String getNick() {
    return nick;
  }

  public void setNick(String nick) {
    this.nick = nick;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public String getRoom() {
    return room;
  }

  public void setRoom(String room) {
    this.room = room;
  }

}

public class ChatServer {
  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate(16384);

  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();

  // Encoder for outgoing text
  static private final CharsetEncoder encoder = Charset.forName("UTF8").newEncoder();

  static private HashMap<Socket, SocData> socMap = new HashMap<>();
  static private HashMap<String, HashSet<Socket>> roomMap = new HashMap<>();

  static private String start = "";

  static public void main(String args[]) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt(args[0]);

    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking(false);

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress(port);
      ss.bind(isa);

      // Create a new Selector for selecting
      Selector selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register(selector, SelectionKey.OP_ACCEPT);
      System.out.println("Listening on port " + port);

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if (key.isAcceptable()) {

            // It's an incoming connection. Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            System.out.println("Got connection from " + s);

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            sc.configureBlocking(false);

            socMap.put(s, new SocData());

            // Register it with the selector, for reading
            sc.register(selector, SelectionKey.OP_READ);

          } else if (key.isReadable()) {

            SocketChannel sc = null;

            try {

              // It's incoming data on a connection -- process it
              sc = (SocketChannel) key.channel();

              boolean ok = processInput(key);

              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) {
                key.cancel();
                Socket s = null;
                try {
                  s = sc.socket();
                  String room;
                  if(!(room = socMap.get(s).getRoom()).isEmpty()){
                    roomMap.get(room).remove(s);
                    broadcast(1, room, socMap.get(s).getNick());
                  } 
                  socMap.remove(s);
                  System.out.println("Closing connection to " + s);
                  s.close();
                } catch (IOException ie) {
                  System.err.println("Error closing socket " + s + ": " + ie);
                }
              }

            } catch (IOException ie) {

              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
                Socket s = sc.socket();
                String room;
                if(!(room = socMap.get(s).getRoom()).isEmpty()){
                  roomMap.get(room).remove(s);
                  broadcast(1, room, socMap.get(s).getNick());
                } 
                socMap.remove(s);
              } catch (IOException ie2) {
                System.out.println(ie2);
              }

              System.out.println("Closed " + sc);
            }
          }
        }

        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch (IOException ie) {
      System.err.println(ie);
    }
  }

  // Just read the message from the socket and send it to stdout
  static private boolean processInput(SelectionKey k) throws IOException {
    // Read the message to the buffer
    SocketChannel sc = (SocketChannel) k.channel();
    Socket s = sc.socket();
    SocData sd = socMap.get(s);
    StringBuffer sb = sd.sb;

    buffer.clear();
    sc.read(buffer);
    buffer.flip();

    // If no data, close the connection
    if (buffer.limit() == 0) {
      return false;
    }

    String msg = decoder.decode(buffer).toString();
    sb.append(msg);
    int index = sb.indexOf("\n");
    if(index==-1) return true;
    String message = sb.substring(0, index);
    sb.delete(0, index + 1);
    if(message.charAt(0)=='/' && message.charAt(1)=='/') message = message.substring(1);

    String[] splitMessage = message.split(" ", -1);
    if(message.length()==0 || splitMessage.length==0) return true;

    switch (splitMessage[0]) {
      case ("/nick"):
        for (SocData it : socMap.values()) { // If nick is used, return
          if (it.getNick().equals(splitMessage[1])) {
            setError(sc);
            return true;
          }
        }
        String old = sd.getNick();
        sd.setNick(splitMessage[1]);
        setOK(sc);
        if (sd.getStatus() == 2) { // Only broadcast if it's in a room
          broadcast(1, sd.getRoom(), old, splitMessage[1]);
          return true;
        }
        sd.setStatus(1);
        break;
      case ("/join"):
        if (sd.getStatus() == 0) {
          setError(sc);
          return true;
        }
        String roomName = splitMessage[1];
        if (!roomMap.containsKey(roomName))
          roomMap.put(roomName, new HashSet<Socket>());
        broadcast(0, roomName, sd.getNick());
        roomMap.get(roomName).add(s);
        if (sd.getStatus() == 2) { // If was in another room
          roomMap.get(sd.getRoom()).remove(s);
          broadcast(1, sd.getRoom(), sd.getNick());
        } else
          sd.setStatus(2);
        sd.setRoom(roomName);
        setOK(sc);
        break;
      case ("/leave"):
        if (sd.getStatus() == 2) {
          roomMap.get(sd.getRoom()).remove(s);
          sd.setStatus(1);
          sd.setRoom("");
          setOK(sc);
          broadcast(1, sd.getRoom(), sd.getNick());
        } else {
          setError(sc);
        }
        break;
      case ("/bye"):
        writeMsg(sc, "BYE");
        if (sd.getStatus() == 2) {
          roomMap.get(sd.getRoom()).remove(s);
          broadcast(1, sd.getRoom(), sd.getNick());
        }
        sd = new SocData();
        return false;
      case ("/priv"):
        if(sd.getStatus()==0 || splitMessage.length < 3) {
          setError(sc);
          return true;
        }
        String target = splitMessage[1];
        for(Entry<Socket, SocData> it : socMap.entrySet()) {
          String nick = it.getValue().getNick();
          if(nick.equals(target) && !nick.equals(sd.getNick())){
            String fin = "PRIVATE " + sd.getNick();
            for(int i = 2; i < splitMessage.length; i++)
              fin += " " + splitMessage[i];
            writeMsg(it.getKey().getChannel(), fin);
            setOK(sc);
            return true;
          }
        }
        setError(sc);
        return true;
      default:
        if (sd.getStatus() != 2) {
          setError(sc);
          return true;
        }
        broadcast(0, sd.getRoom(), sd.getNick(), message);
        break;
    }

    return true;
  }

  static private void setError(SocketChannel sc) throws IOException {
    writeMsg(sc, "ERROR");
  }

  static private void setOK(SocketChannel sc) throws IOException {
    writeMsg(sc, "OK");
  }

  static private void broadcast(int id, String room, String name) throws IOException { // 0 - JOINED ; 1 - LEFT
    String msg = "";
    if (id == 0) {
      msg += "JOINED ";
    } else {
      msg += "LEFT ";
    }
    msg += name;
    for (Socket soc : roomMap.get(room)) {
      SocketChannel sc = soc.getChannel();
      writeMsg(sc, msg);
    }
  }

  static private void broadcast(int id, String room, String first, String second) throws IOException { // 0 - MESSAGE ; 1 - NEWNICK
    String msg = "";
    if (id == 0) {
      msg += "MESSAGE ";
    } else {
      msg += "NEWNICK ";
    }
    msg += first + " ";
    msg += second + " ";
    for (Socket soc : roomMap.get(room)) {
      SocketChannel sc = soc.getChannel();
      writeMsg(sc, msg);
    }
  }

  static private void writeMsg(SocketChannel sc, String message) throws IOException {
    // System.out.println("Sending message : " + message);
    sc.write(encoder.encode(CharBuffer.wrap(message + '\n')));
  }

}