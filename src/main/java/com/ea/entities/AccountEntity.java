package com.ea.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ACCOUNT")
public class AccountEntity {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String pass;

    private String mail;

    private String loc;

    private String born;

    private String zip;

    private String gend;

    private String spam;

    private int tos;

    private String tick;

    private String gamecode;

    private String vers;

    private String sku;

    private String slus;

    private String sdkvers;

    private String builddate;

    private Timestamp createdOn;

    private Timestamp updatedOn;

    @OneToMany(mappedBy="account", fetch = FetchType.EAGER)
    @OrderBy("id DESC")
    private Set<PersonaEntity> personas;

}
