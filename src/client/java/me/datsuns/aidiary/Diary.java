package me.datsuns.aidiary;

import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Diary {
    public String FileName;
    public String SaveDir;
    public Path DiaryPath;
    public File DiaryFile;

    Diary(String directory, String filename) {
        this.FileName = filename;
        this.SaveDir = "";
        this.DiaryPath = null;
        this.DiaryFile = null;
        if( directory.equalsIgnoreCase("")){
            AIDiaryClient.LOGGER.info("directory is not fixed. skip");
            return;
        }
        this.SaveDir = directory;
        this.DiaryPath = Paths.get(this.SaveDir).resolve(this.FileName);
        this.DiaryFile = this.DiaryPath.toFile();
        AIDiaryClient.LOGGER.info("diary path: {}", this.DiaryPath.toAbsolutePath().toString());
    }

    public void notifySaveDirectoryFixed(String directory) {
        this.SaveDir = directory;
        this.DiaryPath = Paths.get(this.SaveDir).resolve(this.FileName);
        this.DiaryFile = this.DiaryPath.toFile();
        AIDiaryClient.LOGGER.info("diary path: {}", this.DiaryPath.toAbsolutePath().toString());
    }

    public void onSave() {
        if( this.DiaryFile == null ) {
            AIDiaryClient.LOGGER.info("save target is not fixed");
            return;
        }
        AIDiaryClient.LOGGER.info("save diary");
    }
}