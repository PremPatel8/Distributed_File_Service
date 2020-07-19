import javafx.util.Pair;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.nio.file.StandardWatchEventKinds.*;

public class Client {
    final String sharedDirPath = "D:/Distributed Systems/Labs/Lab_1/client_1/shared_directory/";
    public WatchKey key = null;
    Set<String> allFiles = new HashSet<>();
    String sName = null;
    String role = "";
    String deletedFileName = "";
    String noOfClts = "";
    int noOfConnectedClts = 0;
    String cltNo = "";
    Socket socket = null;
    BufferedReader in;
    PrintWriter out;
    DataInputStream dis = null;
    DataOutputStream dos = null;
    JFrame jframe = new JFrame("Client");
    JTextField jtextField = new JTextField(50);
    JTextArea jtextarea = new JTextArea(18, 60);
    int voteCount = 0;
    Boolean ignore = false;
    //    Map<String, String> fileEvents = new LinkedHashMap<String, String>();
    Queue<Pair<String, String>> fileEvents = new LinkedList<Pair<String, String>>();

    public Client() {
        jtextarea.setEditable(false);
        jtextField.setEditable(false);
        jframe.getContentPane().add(jtextField, "South");
        jframe.getContentPane().add(new JScrollPane(jtextarea), "Center");
        jframe.pack();
    }

    public static void main(String[] args) throws Exception {
        Client client = new Client();
        client.jframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        client.jframe.setVisible(true);
        client.execute();
    }

    // run method to start communication between client and the server by passing IP address and port.number.
    private void execute() throws IOException, ParseException {
        socket = new Socket("127.0.0.1", 59898); // To initialize a new socket connection

        in = new BufferedReader(new InputStreamReader(socket.getInputStream())); // instance of Buffer Reader for accepting the messages coming from the server.
        out = new PrintWriter(socket.getOutputStream(), true); // instance of PrintWriter for sending the messages to the client.

        //getting the data input and output stream using client socket
        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());

        jtextField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Get the data
                out.println(jtextField.getText() + Integer.toString(jtextField.getText().length()));
                jtextField.setText("");
            }
        });

        try {
            while (true) {
                System.out.println("Beginning of while loop");
                String command = in.readLine();

                System.out.println("command received from server = " + command);

                if (command.startsWith("SUBMITNAME")) {
                    out.println(getUserName()); // Send the desired screen name to the server for acceptance
                } else if (command.startsWith("REDUNDANT")) {
                    JOptionPane.showMessageDialog(null, "Given Username already exist. Please choose another username");
                } else if (command.startsWith("NAMEACCEPTED")) {
                    //Server checks name acceptance

                    jtextField.setEditable(true);
                    sName = command.split(",")[1];
                    jtextarea.append(sName + "\nGiven Username is accepted, Start monitoring Shared Folder\n");

                    //Start shared folder monitoring after client username is accepted by the server

//                    Thread thrd = new Thread(new MonitorSharedDirectory());
//                    thrd.start();

                    MonitorSharedDirectory mon = new MonitorSharedDirectory();
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    Future<?> future = executor.submit(mon);
                    executor.shutdown();

                    System.out.println("end of NAMEACCEPTED");
                } else if (command.contains("disconnected")) {
                    jtextarea.append(command + "\n");
                } else if (command.contains("connected")) {
                    jtextarea.append(command + "\n"); // Connected clients
                } else if (command.startsWith("DELETE")) {
                    System.out.println("inside DELETE command sent by the server");

                    deletedFileName = in.readLine();

                    System.out.println("the name of the file to be deleted = " + deletedFileName);

                    role = "participant";

                    System.out.println("role participant");

                    jtextarea.append("Client role changed to " + role + "\n");

                    Boolean decision = randomVote();

                    if (decision) {
                        System.out.println("this client voted YES");
                        dos.writeUTF("YES");
                        dos.flush();
                        jtextarea.append("This Client voted YES\n");
                    } else {
                        System.out.println("this client voted NO");
                        dos.writeUTF("NO");
                        dos.flush();
                        jtextarea.append("This Client voted NO\n");
                    }
                } else if (command.startsWith("ABORT")) {
                    jtextarea.append("Global Abort received, file will not be deleted\n");
                } else if (command.startsWith("COMMIT")) {
                    String temp = "";

                    ignore = true;
                    jtextarea.append("Global Commit received, the file " + deletedFileName + " will be deleted\n");

                    File f = new File(sharedDirPath + deletedFileName);
                    boolean flag;

                    flag = f.delete();
                    temp = "File deleted";

                    if (flag) {
                        dos.writeUTF(temp);
                        dos.flush();
                    } else
                        dos.writeUTF("Not deleted. Error occured.");
                        dos.flush();
                } else if (command.startsWith("YES") || command.startsWith("NO")) {
                    System.out.println("inside YES or NO command");

                    boolean flag = false;

                    if (command.equals("NO")) {
//                        flag = true;
                        voteCount--;
                    } else if (command.equals("YES")) {
                        voteCount++;
                    }

                    if (voteCount == noOfConnectedClts - 1) {
                        System.out.println("COMMIT action called");

                        //Send a global commit to delete the file from all clients
                        dos.writeUTF("COMMIT");
                        dos.flush();
                    } else if (voteCount == 0 || voteCount == -(noOfConnectedClts - 1)) {
                        System.out.println("ABORT action called");
                        //Send global abort message and the name of the file to restore from the server
                        dos.writeUTF("ABORT");
                        dos.flush();
                        dos.writeUTF(deletedFileName);
                        dos.flush();

                        ignore = true;

                        //Download the backup of the deleted file from the server
                        downloadFile();

                    }
                } else if (command.startsWith("CLTNO")) {
                    cltNo = in.readLine();
                    System.out.println("cltNo = " + cltNo);

                    noOfConnectedClts = Integer.parseInt(cltNo);
                } else if (command.startsWith("DOWNLOAD")){
                    ignore = true;
                    downloadFile();
                }

                System.out.println("end of while loop");
            }
        } catch (Exception e1) {
            System.out.println(e1);
            socket.close();
            jtextarea.append("The Server is offline");
        }
    }

    private String getUserName() {
        return JOptionPane.showInputDialog(jframe, "Please enter your username:", "Name",
                JOptionPane.PLAIN_MESSAGE);
    }

    private Boolean randomVote() throws InterruptedException {
        Thread.sleep(3000);
        Random ran = new Random();
        return ran.nextBoolean();
    }

    private void voteRequests(String eventType, String vrFileName) throws IOException {
        System.out.println("Inside voteRequest eventType = " + eventType);
        System.out.println("Inside voteRequest vrFileName = " + vrFileName);

        dos.writeUTF("COORD");
        dos.flush();

        dos.writeUTF("DELETE," + vrFileName);
        dos.flush();

        System.out.println("After writeUTF to send DELETE command to the server");

        role = "coordinator";
        System.out.println("role coordinator");
        jtextarea.append("Client role changed to " + role + "\n");
    }

    public void uploadFile(String eventType, String uploadFilePath) throws IOException, InterruptedException {
        BufferedInputStream bis = null;
        FileInputStream fis = null;

        System.out.println("Inside uploadFile eventType = " + eventType);
        System.out.println("Inside uploadFile uploadFilePath = " + uploadFilePath);


        // sending new file to server
        File myFile = new File(uploadFilePath);
        byte[] fileContents = new byte[(int) myFile.length()];

        fis = new FileInputStream(myFile);
        bis = new BufferedInputStream(fis);
        DataInputStream dataInpStrm = new DataInputStream(bis);

        dataInpStrm.readFully(fileContents, 0, fileContents.length);

        dos.writeUTF("UPLOAD");
        dos.flush();

        dos.writeUTF(myFile.getName());
        dos.flush();

        dos.writeLong(fileContents.length);
        dos.flush();

        dos.write(fileContents, 0, fileContents.length);
        dos.flush();

        System.out.println("File " + uploadFilePath + " uploaded to Server.");

        jtextarea.append("File " + uploadFilePath + " uploaded to Server." + "\n");

        if (fis != null)
            fis.close();
        if (bis != null)
            bis.close();
//        if (dataInpStrm != null)
//            dataInpStrm.close();
    }

    public void downloadFile() throws IOException {
        // receive file from server
        System.out.println("inside downloadFile()");

        FileOutputStream fos = null;

        if (dis != null){
            System.out.println("dis not null");
        }

        String fileName = dis.readUTF();

        long size = dis.readLong();

        deletedFileName = fileName;
        System.out.println("file sent by server = " + fileName);

        fos = new FileOutputStream(sharedDirPath + fileName);

        int bytesRead = 0;


        byte[] buffer = new byte[(int) size];

        while (size > 0 && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
            fos.write(buffer, 0, bytesRead);
            size -= bytesRead;
        }
        System.out.println("File " + fileName + " downloaded from server(" + size + " bytes read)");

        jtextarea.append("File " + fileName + " downloaded from server\n");

        ignore = true;

        if (fos != null)
            fos.close();
    }


    public class MonitorSharedDirectory implements Runnable {
        long lastModi = 0; //above for loop
        //        final String sharedDirPath = "D:/Distributed Systems/Labs/Lab_1/client_1/shared_directory/";

        <T> WatchEvent<T> cast(WatchEvent<?> event) {
            return (WatchEvent<T>) event;
        }

        @Override
        public void run() {
            try (WatchService watcher = FileSystems.getDefault().newWatchService()) {

                Path monDirPath = Paths.get(sharedDirPath);

                key = monDirPath.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

                do {
                    WatchKey watchKey = watcher.take();

                    if (key != watchKey) {
                        System.err.println("WatchKey not recognized!");
                        continue;
                    }

                    for (WatchEvent<?> event : watchKey.pollEvents()) {
                        WatchEvent<Path> ev = cast(event);

                        WatchEvent.Kind eventKind = ev.kind();

                        Path filePath = monDirPath.resolve(ev.context());

                        if (eventKind == OVERFLOW)
                            continue;

//                        if (ignore) {
//                            System.out.println("inside ignore = " + ignore);
//                            ignore = false;
//                            Thread.sleep(100000);
//                            continue;
//                        }

                        if (eventKind == ENTRY_CREATE) {
                            if (ev.context().toString().equals(deletedFileName)){
                                continue;
                            }
                            System.out.println("inside ENTRY_CREATE");
                            uploadFile(eventKind.name(), filePath.toString());
                        } else if (eventKind == ENTRY_MODIFY) {
                            if (ev.context().toString().equals(deletedFileName)){
                                continue;
                            }
                            if (filePath.toFile().lastModified() - lastModi > 1000) {
                                System.out.println("inside ENTRY_MODIFY");
                                uploadFile(eventKind.name(), filePath.toString());
                            }
                        } else if (eventKind == ENTRY_DELETE) {
                            if (ev.context().toString().equals(deletedFileName)){
                                continue;
                            }
                            System.out.println("inside ENTRY_DELETE");
                            deletedFileName = ev.context().toString();
                            voteRequests(eventKind.name(), ev.context().toString());
                        }
                        lastModi = filePath.toFile().lastModified();
                    }

                    // reset key
                    if (!watchKey.reset()) {
                        break;
                    }
                } while (true);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }
    }
}