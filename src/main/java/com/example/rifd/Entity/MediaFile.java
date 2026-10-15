package com.example.rifd.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// a photo / voice note uploaded from the web (visitor or staff report). The alert points to it as "up:<id>[,<id>]".
// Stored in the DB on purpose: free hosting wipes local disks on every restart
@Entity
@Table(name = "media_files")
@Getter
@Setter
@NoArgsConstructor
public class MediaFile extends BaseEntity {

    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;

    @Column(nullable = false, length = 100)
    private String contentType;

    public MediaFile(byte[] data, String contentType) {
        this.data = data;
        this.contentType = contentType;
    }
}