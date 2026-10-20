import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPOutputStream;
/** Reproducible empty GameTest structure; run with Java 21 source-file mode. */
class MakeTestTemplate {
    public static void main(String[] args) throws Exception {
        Path output=Path.of("src/main/resources/data/livingvillages/structure/empty.nbt");
        Files.createDirectories(output.getParent());
        try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(output)))) {
            out.writeByte(10);out.writeUTF("");
            out.writeByte(3);out.writeUTF("DataVersion");out.writeInt(3955);
            out.writeByte(9);out.writeUTF("size");out.writeByte(3);out.writeInt(3);
            out.writeInt(16);out.writeInt(10);out.writeInt(16);
            out.writeByte(9);out.writeUTF("palette");out.writeByte(10);out.writeInt(1);
            out.writeByte(8);out.writeUTF("Name");out.writeUTF("minecraft:air");out.writeByte(0);
            for(String tag:new String[]{"blocks","entities"}) {
                out.writeByte(9);out.writeUTF(tag);out.writeByte(10);out.writeInt(0);
            }
            out.writeByte(0);
        }
        Path restart=Path.of("src/main/resources/data/livingvillages_restart/structure/empty.nbt");
        Files.createDirectories(restart.getParent());
        Files.copy(output,restart,StandardCopyOption.REPLACE_EXISTING);
    }
}
