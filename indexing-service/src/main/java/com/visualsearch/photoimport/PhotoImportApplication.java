package com.visualsearch.photoimport;

import com.visualsearch.indexing.config.IndexingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Arrays;

/** Explicit CLI entry point, outside the normal worker's component scan. */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration(exclude = {RabbitAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class})
@EnableConfigurationProperties(IndexingProperties.class)
public class PhotoImportApplication {
    public static void main(String[] args) {
        int exitCode = 1;
        try {
            var app = new SpringApplication(PhotoImportApplication.class);
            app.setWebApplicationType(WebApplicationType.NONE);
            try (var context = app.run(args)) {
                String csv = context.getEnvironment().getRequiredProperty("photo-import.csv");
                var photos = PhotoCsv.read(Path.of(csv), PhotoCsv.SHA256, 25000);
                String urls = context.getEnvironment().getProperty("photo-import.ai-urls",
                        "http://127.0.0.1:8002");
                var aiUrls = Arrays.stream(urls.split(",", -1)).map(String::trim).toList();
                org.slf4j.LoggerFactory.getLogger(PhotoImportApplication.class).info("Import AI endpoints {}", aiUrls);
                new PhotoImporter(context.getBean(DataSource.class), context.getBean(IndexingProperties.class), aiUrls)
                        .run(photos);
                exitCode = 0;
            }
        } catch (Exception exception) {
            org.slf4j.LoggerFactory.getLogger(PhotoImportApplication.class)
                    .error("Import stopped. Run the same command to resume from the last committed group.", exception);
        }
        System.exit(exitCode);
    }
}
