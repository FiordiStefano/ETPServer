/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ETPServer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.json.Json;
import javax.json.JsonObject;

/**
 * Classe che gestisce il file da ricevere
 * @author Stefano Fiordi
 */
public class FileHandlerServer {

    /**
     * grandezza standard del pacchetto
     */
    private final int NumberOfBytes = 4096;
    /**
     * file da ricevere
     */
    protected File FileToRecv;
    /**
     * numero di pacchetti totale
     */
    protected int nPackets; 
    /**
     * indice del pacchetto da cui iniziare il trasferimento
     */
    protected int startIndex;
    /**
     * digest MD5 del file da ricevere
     */
    protected String fileDigest; 
    /**
     * contatore per i pacchetti errati 
     */
    protected int RetryCount = 0; 

    /**
     * Costruttore che calcola il numero di pacchetti in base alla lunghezza del file e stabilisce l'indice iniziale per i pacchetti
     * @param FileToRecv il file da ricevere
     * @param fileDigest il digest del file in MD5
     * @param length la lunghezza del file da ricevere
     * @throws IOException 
     * @throws NoSuchAlgorithmException
     */
    public FileHandlerServer(File FileToRecv, String fileDigest, int length) throws IOException, NoSuchAlgorithmException {
        this.FileToRecv = new File(FileToRecv.getPath());
        this.fileDigest = fileDigest;

        if (length % NumberOfBytes == 0) {
            nPackets = (int) length / NumberOfBytes;
        } else {
            nPackets = (int) length / NumberOfBytes + 1;
        }

        if (!this.FileToRecv.exists()) {
            this.FileToRecv.createNewFile();
            this.startIndex = 0;
        } else {
            if (checksum() || this.FileToRecv.length()>= length) {
                this.startIndex = -1;
            } else {
                this.startIndex = (int) this.FileToRecv.length() / NumberOfBytes;
            }
        }
    }

    /**
     * Metodo che aggiunge il pacchetto ricevuto al file in caso il numero sia correttor, altrimenti richiede il pacchetto con il numero corretto, per un massimo di tre volte
     * @param JsonPacket Il pacchetto da aggiungere al file
     * @param packetIndex l'indice corretto 
     * @return il pacchetto Json di risposta 
     * @throws IOException 
     */
    public JsonObject addPacket(JsonObject JsonPacket, int packetIndex) throws IOException {
        JsonObject JsonRespPacket;

        if (JsonPacket.getInt("number") == packetIndex) {
            String base64json = JsonPacket.getString("text");
            // decodifico il pacchetto da Base64 a binario
            byte[] packet = Base64.getDecoder().decode(base64json);
            // accodo il pacchetto al file
            Files.write(FileToRecv.toPath(), packet, StandardOpenOption.APPEND);

            JsonRespPacket = Json.createObjectBuilder()
                    .add("type", "resp")
                    .add("resp", "ok")
                    .build();

            RetryCount = 0;
        } else {
            if (RetryCount < 3) {
                JsonRespPacket = Json.createObjectBuilder()
                        .add("type", "resp")
                        .add("resp", "wp") // wp: wrong packet
                        .add("index", packetIndex) // right packet index
                        .build();
                RetryCount++;
            } else {
                JsonRespPacket = Json.createObjectBuilder()
                        .add("type", "resp")
                        .add("resp", "mrr") // mrr: max retry reached
                        .build();
            }
        }

        return JsonRespPacket;
    }

    /**
     * Metodo che crea il pacchetto di risposta al pacchetto di informazioni
     * @return il pacchetto Json di risposta
     */
    public JsonObject getInfoRespPacket() {
        JsonObject JsonInfoRespPacket;
        if (this.startIndex != -1) {
            JsonInfoRespPacket = Json.createObjectBuilder()
                    .add("type", "resp")
                    .add("resp", "ok")
                    .add("index", this.startIndex)
                    .build();
        } else {
            JsonInfoRespPacket = Json.createObjectBuilder()
                    .add("type", "resp")
                    .add("resp", "fae") // fae = file already exists
                    .build();
        }

        return JsonInfoRespPacket;
    }

    /**
     * Metodo che effettua l'hashing MD5 del file ricevuto e lo confronta con il digest del pacchetto informazioni
     * @return true se i digest sono uguali, altrimenti false
     * @throws IOException
     * @throws NoSuchAlgorithmException 
     */
    public final boolean checksum() throws IOException, NoSuchAlgorithmException {
        byte[] byteFile = Files.readAllBytes(FileToRecv.toPath());
        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        messageDigest.update(byteFile);
        byte[] messageDigestMD5 = messageDigest.digest();
        StringBuilder stringBuffer = new StringBuilder();
        for (byte bytes : messageDigestMD5) {
            stringBuffer.append(String.format("%02x", bytes & 0xff));
        }

        return this.fileDigest.equals(stringBuffer.toString());
    }
}
