package vkr.osago.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users", schema = "insurance")
public class UserEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "self_registered", nullable = false)
    private boolean selfRegistered;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "middle_name")
    private String middleName;
}