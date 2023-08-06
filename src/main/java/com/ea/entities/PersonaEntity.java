package com.ea.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
@AllArgsConstructor
@Entity
@Table(name = "PERSONA")
public class PersonaEntity {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name="ACCOUNT_ID", nullable=false)
    private AccountEntity account;

    private String name;

    private long kills;

    private long deaths;

    private Timestamp createdOn;

}