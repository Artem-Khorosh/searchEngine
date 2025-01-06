package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "site")
public class Site {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(columnDefinition = "DATETIME", nullable = false, name = "status_time")
    private LocalDateTime statusTime;

    @Column(columnDefinition = "TEXT",name = "last_error")
    private String lastError;

    @Column( nullable = false)
    private String url;

    @Column( nullable = false)
    private String name;

    @OneToMany(mappedBy = "site")
    private List<Page> pages;



}
