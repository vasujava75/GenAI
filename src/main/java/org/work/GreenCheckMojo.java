package org.work;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.util.List;

import java.net.HttpURLConnection;
import java.net.URL;

@Mojo(name = "green-check", defaultPhase = LifecyclePhase.VERIFY)
public class GreenCheckMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.sourceDirectory}", required = true)
    File sourceDirectory;

    public void execute() throws MojoExecutionException {
        getLog().info("Running green coding checks...");
        getLog().info("Source directory: " + sourceDirectory.getAbsolutePath());

        List<File> javaFiles = null;
        try {
            javaFiles = findJavaFiles(sourceDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (javaFiles == null) {
            getLog().info("Files are empty to analysis");
            return;
        }

        for (File file : javaFiles) {
            try {
                getLog().info("File analysing "+file.getName());
                List<String> lines = Files.readAllLines(file.toPath());
                String content = String.join("\n", lines);
                analyzeWithOllama(content);
            } catch (IOException e) {
                getLog().error("Failed to read file: " + file.getName(), e);
            }
        }
        getLog().info("Green coding analysis complete.");
    }

    private List<File> findJavaFiles(File dir) throws IOException {
        return Files.walk(dir.toPath())
                .filter(path -> path.toString().endsWith(".java"))
                .map(java.nio.file.Path::toFile)
                .toList();
    }


    private  void analyzeWithOllama(String code) {
            try {
                URL url = new URL("http://localhost:11434/api/generate");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                // Build JSON payload safely
                JSONObject json = new JSONObject();
                json.put("model", "llama3.1:latest");
                json.put("prompt", "Analyze this Java code for energy efficiency:\n" + code);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                System.out.println("Ollama response code: " + responseCode);

                if (responseCode == 200) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                        String line;
                        StringBuilder fullResponse = new StringBuilder();
                        while ((line = br.readLine()) != null) {
                            if (!line.trim().isEmpty()) {
                                JSONObject chunk = new JSONObject(line);
                                if (chunk.has("response")) {
                                    fullResponse.append(chunk.getString("response"));
                                }
                            }
                        }
                        getLog().info("Ollama response: " + fullResponse.toString());
                    }
                } else {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                        StringBuilder error = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            error.append(line.trim());
                        }
                        getLog().error("Error response: " + error);
                    }
                }

            } catch (Exception e) {
                getLog().error(e.getMessage());
            }
        }
    }


