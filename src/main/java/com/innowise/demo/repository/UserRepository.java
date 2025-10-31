package com.innowise.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.innowise.demo.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    User getUserById(Long aLong);

    /**
     * ADD  methods!!!
     * @return
     */
    List<User> getUsersByEmail(String email);

    /// /////////////////////////////////////
    //Используется @NamedQuery из User.java
    Optional<User> findByEmailNamed(@Param("email") String email);

    //JPQL запрос
    @Query("SELECT u from User u where u.email=:email")
    Optional<User> findByEmailJPQL(@Param("email") String email);

    //Native Sql
    @Query(value = "SELECT * from userservice_data.users u where u.email = :email", nativeQuery=true)
    Optional<User> findByEmailNativeQuery(@Param("email") String email);

    Page<User> findAll(Pageable pageable);
    //ну или так для решения проблемы ленивой инициализации:
//    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.cards")
//    Page<User> findAllWithCards(Pageable pageable);
}
