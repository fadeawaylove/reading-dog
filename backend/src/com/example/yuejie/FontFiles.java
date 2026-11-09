package com.example.yuejie;
import java.nio.file.*;
final class FontFiles {
 static Path resolve(Path data,String path){if(!path.matches("/fonts/(?:catalog\\.json|reader-body\\.ttf|reader-serif\\.ttf)"))return null;Path file=data.resolve(path.substring(1));return Files.isRegularFile(file)?file:null;}
}
