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
  //  void createUser(User user);

    User getUserById(Long aLong);

    /**
     * ADD  methods!!!
     * @return
     */
  //  List<User> getAllWithPagination();

    List<User> getUsersByEmail(String email);

//    @Override
//    Page<User> findAll(Pageable pageable);

   // User updateUser(User user);

  //  void deleteUserById(Long id);

   // User findByUsername(String username);

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
}
