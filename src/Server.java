import javax.swing.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {

    // connections of all the
    // clients
    private static final int PORT = 59898; // The port number on which the server listens to the client
    private static final String serversharedDirPath = "D:/Distributed Systems/Labs/Lab_1/server/";
    static JTextArea jtextarea = new JTextArea(70, 70);
    static ClientHandler currentCoordintator = null;
    private static ArrayList<String> cnames = new ArrayList<String>(); // ArrayList to store just the list of names of
    // the connected clients
    private static Map<String, ClientHandler> connectedClients = new HashMap<String, ClientHandler>(); // Hashmap to store the list of connected clients
    JFrame jframe = new JFrame("Server");
    // Initializing the constructor to create a GUI

    public Server() {
        jtextarea.setEditable(true);
        jframe.getContentPane().add(new JScrollPane(jtextarea), "Center");
        jframe.pack();
        jtextarea.append("\n The Server is running, listening for connections\n");

    }

    // Main method to initialize the server
    public static void main(String[] args) throws Exception {
//        Vector<ClientHandler> connectedClients = new Vector<>();
//        ArrayList<String> clientNames = new ArrayList<>();

        ExecutorService es = null;
        Server server = new Server();

        server.jframe.setVisible(true);

        try (ServerSocket listener = new ServerSocket(PORT)) {
            es = Executors.newCachedThreadPool();

            while (true) {
                Socket clientSocket = listener.accept();

                ClientHandler clt = new ClientHandler(clientSocket);

                es.execute(clt);
            }
        } catch (Error e) {
            System.out.println(e);
        } finally {
            try {
                assert es != null;
                if (!es.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    es.shutdownNow();
                }
            } catch (InterruptedException e) {
                es.shutdownNow();
            }
        }
    }

    // The Client Class handles a clients connections with other clients
    private static class ClientHandler implements Runnable {
        FileOutputStream fos = null;
        DataInputStream dis = null;
        DataOutputStream dos = null;
        boolean running = true;
        String command = "";
        int bytesRead = 0;
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        private Socket socket;
        private String cname;
        private BufferedReader in;
        private PrintWriter out;
        private String fileToDel;

        // Constructor to start a connection with other client
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        // Method to give a client their name
        @Override
        public void run() {
            try {
                // Create character streams for the socket.
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Create file streams for the socket, to receive file from client
                dis = new DataInputStream(socket.getInputStream());
                dos = new DataOutputStream(socket.getOutputStream());

                // While loop runs till a user enters a unique name for a client by making comparisons to the Array list of client names.
                while (true) {
                    out.println("SUBMITNAME");
                    cname = in.readLine();
                    if (cname == null) {
                        return;
                    }
                    if (cname.equals(".*-")) {
                        break;
                    }
                    synchronized (cnames) {
                        if (!cnames.contains(cname)) {
                            cnames.add(cname);
                            break;
                        } else {
                            out.println("REDUNDANT");
                        }
                    }
                }

                out.println("NAMEACCEPTED," + cname); // Here we accept a clients name and add its connection to hashmap.

                connectedClients.put(cname, this);

//                sendMsgToAllConnectedClts("\n" + cname + " is now connected\n");

                System.out.println("The client " + cname + " has now been connected");

                jtextarea.append(cname + " is connected\n");

//                System.out.println(connectedClients);

                // Using the while loop to take commands from client and execute required functions
                while (running) {
                    //Reading command from client and choosing the action to be taken based on the command.
                    System.out.println("beginning of the while loop");
                    command = dis.readUTF();

                    if (command.contains(",")) {
                        System.out.println("inside , check");
                        String[] arrOfStr = command.split(",");

                        command = arrOfStr[0];
                        fileToDel = arrOfStr[1];

                        System.out.println("command receive = " + command + " From client = " + this.cname);
                        System.out.println("fileToDel = " + fileToDel);
                    }

                    jtextarea.append("Command receive: " + command + " From client = " + this.cname + "\n");

                    System.out.println("command receive = " + command + " From client = " + this.cname);


                    if (command.contentEquals("UPLOAD")) {
                        // For broadcasting Delete Request to all connected clients.
                        // To upload a file from a client and send it to all other connected clients.
                        String fileName = dis.readUTF();

                        System.out.println("file = " + fileName + " sent by client= " + this.cname);

                        this.fos = new FileOutputStream(serversharedDirPath + fileName);

                        //get the size of the file to be received
                        long size = dis.readLong();

                        byte[] buffer = new byte[(int) size];

                        //write the data bytes received to a file
                        while (size > 0 && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
                            fos.write(buffer, 0, bytesRead);
                            size -= bytesRead;
                        }

                        System.out.println("Upload File " + fileName + " downloaded from " + this.cname);

                        jtextarea.append("File " + fileName + " downloaded from " + this.cname);

                        //send the file to all the connected clients
                        final String FILE_TO_SEND = serversharedDirPath + fileName;

                        System.out.println("FILE_TO_SEND = " + FILE_TO_SEND);

                        File myFile = new File(FILE_TO_SEND);
                        byte[] fileContents = new byte[(int) myFile.length()];

                        fis = new FileInputStream(myFile);
                        bis = new BufferedInputStream(fis);
                        // disB = new DataInputStream(bis);

                        //fill the data into the Byte array?
                        bis.read(fileContents, 0, fileContents.length);

                        // Sending file to each connected client
                        for (ClientHandler clts : connectedClients.values()) {
                            System.out.println("inside for loop to send file to connected clients");
                            if (clts.cname != this.cname) {
                                System.out.println("inside if");
                                //Send the file name to the client

                                clts.out.println("DOWNLOAD");

                                clts.dos.writeUTF(myFile.getName());

                                clts.dos.flush();

                                //send the length of the file to the client
                                clts.dos.writeLong(fileContents.length);
                                clts.dos.flush();

                                System.out.println("Sending the file " + FILE_TO_SEND + " (" + fileContents.length + " bytes) to client = " + clts.cname);

                                //send the file contents to the client?
                                clts.dos.write(fileContents, 0, fileContents.length);
                                clts.dos.flush();
                            }
                        }
                        fis.close();
                        bis.close();
                    } else if (command.contentEquals("DELETE")) {
                        // To broadcasting global commit to all clients
                        System.out.println("inside DELETE command actions");

                        //Sending the no of connected clients to the coordinator only

                        int no_of_cnctd_clts = connectedClients.size();

                        System.out.println("no_of_cnctd_clts integer = " + no_of_cnctd_clts);

                        String cltNo = "";

                        cltNo = Integer.toString(no_of_cnctd_clts);

                        System.out.println("cltNo String = " + cltNo);

//                        if (dos != null){
//                            System.out.println("inside dos != null");
//                            this.dos.writeUTF(cltNo);
//                        }
//                        else
//                            System.out.println("DOS is NULL");

//                        dos.flush();

//                        dos.writeInt(no_of_cnctd_clts);

//                        dos.writeUTF(String.valueOf(connectedClients.size()));

                        currentCoordintator.out.println("CLTNO");
                        currentCoordintator.out.println(cltNo);

                        System.out.println("after sending the total number of connected clients to the coordinator client");

                        for (ClientHandler clts : connectedClients.values()) {
                            System.out.println("inside For Loop to send DELETE command to all the participant clients");
                            if (!clts.cname.equals(this.cname)) {
                                System.out.println("clts.cname = " + clts.cname);
                                clts.out.println("DELETE");
                                clts.out.println(fileToDel);
                            }
                        }

                        System.out.println("after for loop to send Delete command to all participant clients");

//                        String reply = connectedClients.get("cl2").dis.readUTF();
//                        System.out.println("reply = " + reply);
//
//                        reply = connectedClients.get("cl3").dis.readUTF();
//                        System.out.println("reply = " + reply);

//                        for (ClientHandler clts : connectedClients.values()) {
//                            System.out.println("inside For Loop to receive reply from all the participant clients");
//                            String reply = clts.dis.readUTF();
//                            System.out.println("reply = "+reply);
//                            participantDecisions.add(reply);
//                        }
//
//                        System.out.println("after reply for loop");
//
//                        for (String decsns : participantDecisions) {
//                            System.out.println("inside For Loop to send all the replies back to the coordinator client");
//                            this.dos.writeUTF(decsns);
//                            this.dos.flush();
//                        }

                        System.out.println("end of DELETE command");
                    } else if (command.contentEquals("COMMIT")) {
                        // To broadcasting global abort to all clients
//                        sendMsgToAllConnectedClts("COMMIT");

                        for (ClientHandler clts : connectedClients.values()) {
                            if (!clts.cname.equals(currentCoordintator.cname)) {
                                System.out.println("clts.cname = " + clts.cname);
                                clts.out.println("COMMIT");
                            }
                        }
                    } else if (command.contentEquals("ABORT")) {
                        // To let a user end the connection and close his window.
                        System.out.println("inside ABORT action");
                        String deletedFileName = dis.readUTF();

                        System.out.println("deletedFileName = " + deletedFileName);

                        final String FILE_TO_SEND = serversharedDirPath + deletedFileName;

                        System.out.println("FILE_TO_SEND = " + FILE_TO_SEND);

                        File myFile = new File(FILE_TO_SEND);

                        byte[] fileContents = new byte[(int) myFile.length()];

                        fis = new FileInputStream(myFile);
                        bis = new BufferedInputStream(fis);

                        bis.read(fileContents, 0, fileContents.length);

                        System.out.println("currentCoordintator = " + currentCoordintator.cname);

                        currentCoordintator.dos.writeUTF(myFile.getName());
                        currentCoordintator.dos.flush();

                        System.out.println("after sending file name to coordinator");

                        currentCoordintator.dos.writeLong(fileContents.length);
                        currentCoordintator.dos.flush();

                        System.out.println("after sending file length to coordinator");

                        //send the backup file contents to the currentCoordintator
                        currentCoordintator.dos.write(fileContents, 0, fileContents.length);
                        currentCoordintator.dos.flush();

                        fis.close();
                        bis.close();

                        System.out.println("after sending the backup file back to the coordinator, end of ABORT section");
                    } else if (command.contentEquals("QUIT")) {
                        System.out.println("Stopping client thread for client : " + this.cname);
                        running = false;
                        //Closing all streams and sockets.
                        dos.flush();
                        dis.close();
                        dos.close();
                        socket.close();
                        break;
                    } else if (command.contentEquals("YES") || command.contentEquals("NO")) {
                        //passing the reply from the participants to the coordinator
                        System.out.println("inside command YES or NO");
                        currentCoordintator.out.println(command);
                        currentCoordintator.out.flush();
                    } else if (command.contentEquals("COORD")) {
                        currentCoordintator = connectedClients.get(this.cname);
                    }

//                    jtextarea.append("Message from the client  " + cname + "\n");
                    System.out.println("end of the while loop");
                }
            } catch (IOException ex) {
                System.out.println(ex);
            } finally {
                // To close a connection and disconnect the client and notify the server that the client has been disconnected.
                if (cname != null) {
                    cnames.remove(cname);
                    System.out.println("discons1");
                }

                if (out != null) {
                    connectedClients.remove(out);
                    for (ClientHandler clts : connectedClients.values()) {
                        clts.out.println(cname + " is disconnected\n");
                        System.out.println("has been broadcasted");
                    }
                    jtextarea.append(cname + " is disconnected\n");

                    System.out.println("is discon");
                }
                try {
                    socket.close(); // closing a socket connection

                } catch (IOException ex) {
                    System.out.println(ex);
                }
            }
        }

        void sendMsgToAllConnectedClts(String msg) {
            System.out.println("inside sendMsgToAllConnectedClts()");
            for (ClientHandler clts : connectedClients.values()) {
                System.out.println("clts.cname = " + clts.cname);
                clts.out.println(msg);
            }
        }
    }
}