/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ETPServer;

import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;
import javax.swing.text.DefaultCaret;

/**
 * Programma server per il trasferimento di file utilizzando il protocollo ETP
 *
 * @author Stefano Fiordi
 */
public class ETPServer extends JFrame {

    JTextArea monitor;
    JScrollPane monitorScroll;
    JTextField folderText;
    JButton chooseButton;
    JButton startButton;
    JProgressBar progressBar;
    JFileChooser dirChooser;
    ServerSocket ssock;
    Socket socket;
    BufferedWriter out;
    BufferedReader in;
    FileHandlerServer fhs;
    Thread thServer;
    File downloadFolder;

    /**
     * Costruttore nel quale viene inizializzata la GUI
     */
    public ETPServer() {
        int width = (Toolkit.getDefaultToolkit().getScreenSize().width * 34) / 100;
        int heigth = (Toolkit.getDefaultToolkit().getScreenSize().height * 15) / 100;
        int x = (Toolkit.getDefaultToolkit().getScreenSize().width * 33) / 100;
        int y = (Toolkit.getDefaultToolkit().getScreenSize().height * 20) / 100;
        super.setBounds(x, y, width, heigth);
        super.setResizable(false);
        super.setTitle("ETPServer");
        super.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        super.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (JOptionPane.showConfirmDialog(ETPServer.this, "Sei sicuro di voler uscire?", "Esci", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                    System.exit(0);
                }
            }
        });
        folderText = new JTextField("Download/");
        folderText.setEditable(false);
        this.add(folderText, BorderLayout.PAGE_START);
        monitor = new JTextArea();
        monitor.setEditable(false);
        //this.add(monitor, BorderLayout.CENTER);
        monitorScroll = new JScrollPane(monitor, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        DefaultCaret caret = (DefaultCaret) monitor.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        this.add(monitorScroll, BorderLayout.CENTER);
        chooseButton = new JButton("Sfoglia...");
        this.add(chooseButton, BorderLayout.LINE_START);
        chooseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser dirChooser = new JFileChooser();
                dirChooser.setApproveButtonText("Seleziona");
                dirChooser.setApproveButtonToolTipText("Seleziona la cartella");
                dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                dirChooser.setAcceptAllFileFilterUsed(false);
                int result = dirChooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    if (dirChooser.getSelectedFile().exists()) {
                        downloadFolder = dirChooser.getSelectedFile();
                        folderText.setText(downloadFolder.getPath());
                    } else {
                        JOptionPane.showMessageDialog(ETPServer.this, "Directory inesistente");
                    }
                }
            }
        });
        startButton = new JButton("Start Server");
        this.add(startButton, BorderLayout.LINE_END);
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (thServer == null) {
                    startETPServer();
                    thServer.start();
                } else {
                    JOptionPane.showMessageDialog(ETPServer.this, "Il server è già in esecuzione");
                }
            }
        });
        progressBar = new JProgressBar(JProgressBar.HORIZONTAL, 0, 100);
        progressBar.setValue(0);
        progressBar.setString("0%");
        progressBar.setStringPainted(true);
        this.add(progressBar, BorderLayout.PAGE_END);
    }

    /**
     * Metodo che si occupa dell'inizializzazione delle socket e del
     * trasferimento del file
     */
    protected void startETPServer() {
        thServer = new Thread(new Runnable() {
            @Override
            public void run() {
                int errorCounter = 0; // contatore errori di connessione
                while (true) {
                    try {
                        // creazione della socket del server
                        ssock = new ServerSocket(4000);
                        //System.out.println("Socket in ascolto...");
                        monitor.append("Socket in ascolto sulla porta " + ssock.getLocalPort() + "\n");
                        // creazione della socket per il trasferimento dati
                        socket = ssock.accept();
                        monitor.append("Collegamento al client effettuato\n");

                        errorCounter = 0;

                        // Inizializzazione delle stream dalla socket
                        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        // Creazione delle stream di JSON per leggere e scrivere nella stream della socket
                        JsonWriter writer = Json.createWriter(out);
                        JsonReader reader = Json.createReader(in);

                        // lettura del pacchetto informazioni
                        JsonObject JsonInfoPacket = reader.readObject();
                        //System.out.println(JsonInfoPacket.toString());
                        monitor.append("Il client vuole inviare il file:\n Nome: " + JsonInfoPacket.getString("name") + "\n Dimensioni: " + JsonInfoPacket.getInt("length") + "\n Digest: " + JsonInfoPacket.getString("checksum") + "\n");

                        File FileToRecv;
                        if (downloadFolder != null) {
                            FileToRecv = new File(downloadFolder + "/" + JsonInfoPacket.getString("name"));
                        } else {
                            if (Files.notExists(new File("Download/").toPath())) {
                                new File("Download/").mkdir();
                            }
                            FileToRecv = new File("Download/" + JsonInfoPacket.getString("name"));
                        }
                        fhs = new FileHandlerServer(FileToRecv, JsonInfoPacket.getString("checksum"), JsonInfoPacket.getInt("length"));
                        JsonObject JsonInfoRespPacket = fhs.getInfoRespPacket();
                        // invio del pacchetto di risposta al pacchetto informazioni
                        writer.writeObject(JsonInfoRespPacket);
                        out.flush();

                        if (fhs.startIndex != -1) {
                            //System.out.println(fhs.nPackets);
                            monitor.append("Pacchetti: " + fhs.nPackets + "\nInizio download...\n");
                            long time = System.nanoTime();
                            for (int i = fhs.startIndex; i < fhs.nPackets; i++) {
                                reader = Json.createReader(in);
                                // leggo il pacchetto dati
                                JsonObject JsonPacket = reader.readObject();

                                writer = Json.createWriter(out);
                                JsonObject JsonRespPacket = fhs.addPacket(JsonPacket, i);
                                // invio il pacchetto di risposta
                                writer.writeObject(JsonRespPacket);
                                out.flush();
                                if (!JsonRespPacket.getString("resp").equals("mrr")) {
                                    //System.out.println("Pacchetto " + i + " ricevuto correttamente");
                                    float percent = 100f / fhs.nPackets * (i + 1);
                                    progressBar.setValue((int) percent);
                                    progressBar.setString((int) percent + "%");
                                } else {
                                    //System.out.println("Errore durante la ricezione del pacchetto");
                                    JOptionPane.showMessageDialog(ETPServer.this, "Errore durante la ricezione del pacchetto");
                                    break;
                                }
                            }
                            time = (System.nanoTime() - time) / 1000000000;
                            progressBar.setValue(100);
                            progressBar.setString("100%");
                            if (time > 0) {
                                monitor.append("Velocità: " + JsonInfoPacket.getInt("length") / 1024 / time + " KB/s\nDownload completato\n");
                            } else {
                                monitor.append("Velocità: incalcolabile\nDownload completato\n");
                            }

                            if (fhs.checksum()) {
                                JOptionPane.showMessageDialog(ETPServer.this, "File scaricato correttamente");
                            } else {
                                JOptionPane.showMessageDialog(ETPServer.this, "File scaricato non correttamente");
                            }

                            progressBar.setValue(0);
                            progressBar.setString("0%");
                        } else {
                            //System.out.println("Il file esiste già");
                            monitor.append("File già presente nella directory\n");
                        }

                        // chiusura delle stream di Json
                        writer.close();
                        reader.close();

                        monitor.append("Chiusura del server...\n");
                        // chiusura delle socket
                        socket.close();
                        ssock.close();

                        JOptionPane.showMessageDialog(ETPServer.this, "Server chiuso");
                    } catch (IOException | JsonException | NoSuchAlgorithmException ex) {

                        progressBar.setValue(0);
                        progressBar.setString("0%");
                        try {
                            socket.close();
                            ssock.close();
                        } catch (IOException e) {
                            JOptionPane.showMessageDialog(ETPServer.this, "Errore nella chiusura della socket");
                        }
                        JOptionPane.showMessageDialog(ETPServer.this, "Errore: server chiuso");
                        if (errorCounter < 3) {
                            //System.out.println("Errore di comunicazione");
                            errorCounter++;
                        } else {
                            JOptionPane.showMessageDialog(ETPServer.this, "Numero massimo errori raggiunto");
                            break;
                        }
                    }
                }
                JOptionPane.showMessageDialog(ETPServer.this, "Server chiuso");
            }
        });
    }

    /**
     * Main che crea un'istanza della classe
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        new ETPServer().setVisible(true);
    }

}
