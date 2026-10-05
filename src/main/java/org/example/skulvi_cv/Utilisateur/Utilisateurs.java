package org.example.skulvi_cv.Utilisateur;


import jakarta.persistence.*;
import lombok.*;
import org.example.skulvi_cv.roles.Roles;

import javax.swing.*;
import java.util.regex.Pattern;

@Builder
@Getter
@Setter
@Entity
@Table(name = "User")
@AllArgsConstructor
@NoArgsConstructor
public class Utilisateurs {
    @Id
    private  String id;

    @Column(nullable = false, unique = true)
    private String email;

    private String passewods;

    @Builder.Default
    private Roles roles=Roles.CANDIDAT;


}
