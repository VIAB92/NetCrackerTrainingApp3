import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Server {
    private static Logger logger = Logger.getLogger(Server.class);
    private static int uniqueId;
    private List<ClientThread> clientList;
    private ServerGUI serverGUI;
    private SimpleDateFormat simpleDateFormat;
    private int port;
    private boolean keepGoing;

    public Server(int port) {
        this(port, null);
        BasicConfigurator.configure();
    }

    public Server(int port, ServerGUI sg) {
        BasicConfigurator.configure();
        this.serverGUI = sg;
        this.port = port;
        simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
        clientList = new ArrayList<ClientThread>();
    }

    public void start() {
        keepGoing = true;
        try
        {
            ServerSocket serverSocket = new ServerSocket(port);

            while(keepGoing)
            {
                display("Server waiting for Clients on port " + port + ".");
                Socket socket = serverSocket.accept();
                if(!keepGoing)
                    break;
                ClientThread t = new ClientThread(socket);
                clientList.add(t);
                t.start();
            }
            try {
                serverSocket.close();
                for(int i = 0; i < clientList.size(); ++i) {
                    ClientThread tc = clientList.get(i);
                    try {
                        tc.sInput.close();
                        tc.sOutput.close();
                        tc.socket.close();
                    }
                    catch(IOException ioE) {

                    }
                }
            }
            catch(Exception e) {
                display("Exception closing the server and clients: " + e);
            }
        }

        catch (IOException e) {
            String msg = simpleDateFormat.format(new Date()) + " Exception on new ServerSocket: " + e + "\n";
            display(msg);
        }
    }

    protected void stop() {
        keepGoing = false;
        try {
            new Socket("localhost", port);
        }
        catch(Exception e) {
           logger.error(e);
        }
    }

    private void display(String msg) {
        String time = simpleDateFormat.format(new Date()) + " " + msg;
        if(serverGUI == null)
            System.out.println(time);
        else
            serverGUI.appendEvent(time + "\n");
    }

    private synchronized void broadcast(String message) {
        String time = simpleDateFormat.format(new Date());
        String messageLf = time + " " + message + "\n";
        if(serverGUI == null)
            System.out.print(messageLf);
        else
            serverGUI.appendRoom(messageLf);

        for(int i = clientList.size(); --i >= 0;) {
            ClientThread ct = clientList.get(i);
            if(!ct.writeMsg(messageLf)) {
                clientList.remove(i);
                display("Disconnected Client " + ct.username + " removed from list.");
            }
        }
    }

    synchronized void remove(int id) {
        for(int i = 0; i < clientList.size(); ++i) {
            ClientThread ct = clientList.get(i);

            if(ct.id == id) {
                clientList.remove(i);
                return;
            }
        }
    }

    class ClientThread extends Thread {
        Socket socket;
        ObjectInputStream sInput;
        ObjectOutputStream sOutput;
        int id;
        String username;
        ChatMessage cm;
        String date;

        ClientThread(Socket socket) {
            id = ++uniqueId;
            this.socket = socket;
            System.out.println("Thread trying to create Object Input/Output Streams");
            try
            {
                sOutput = new ObjectOutputStream(socket.getOutputStream());
                sInput  = new ObjectInputStream(socket.getInputStream());
                username = (String) sInput.readObject();
                display(username + " just connected.");
            }
            catch (IOException e) {
                display("Exception creating new Input/output Streams: " + e);
                return;
            }
            catch (ClassNotFoundException e) {
            }
            date = new Date().toString() + "\n";
        }

        public void run() {
            boolean keepGoing = true;
            while(keepGoing) {
                try {
                    cm = (ChatMessage) sInput.readObject();
                }
                catch (IOException e) {
                    display(username + " Exception reading Streams: " + e);
                    break;
                }
                catch(ClassNotFoundException e2) {
                    logger.error(e2);
                }
                String message = cm.getMessage();

                switch(cm.getType()) {

                    case ChatMessage.MESSAGE:
                        broadcast(username + ": " + message);
                        break;
                    case ChatMessage.LOGOUT:
                        display(username + " disconnected with a LOGOUT message.");
                        keepGoing = false;
                        break;
                    case ChatMessage.WHOISIN:
                        writeMsg("List of the users connected at " + simpleDateFormat.format(new Date()) + "\n");
                        for(int i = 0; i < clientList.size(); ++i) {
                            ClientThread ct = clientList.get(i);
                            writeMsg((i+1) + ") " + ct.username + " since " + ct.date);
                        }
                        break;
                }
            }
            remove(id);
            close();
        }

        private void close() {
            try {
                if(sOutput != null) sOutput.close();
            }
            catch(Exception e) {
                logger.error(e);
            }
            try {
                if(sInput != null) sInput.close();
            }
            catch(Exception e) {
                logger.error(e);
            };
            try {
                if(socket != null) socket.close();
            }
            catch (Exception e) {
                logger.error(e);
            }
        }

        private boolean writeMsg(String msg) {
            if(!socket.isConnected()) {
                close();
                return false;
            }
            try {
                sOutput.writeObject(msg);
            }
            catch(IOException e) {
                display("Error sending message to " + username);
                display(e.toString());
            }
            return true;
        }
    }
}

