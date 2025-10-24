package com.innowise.demo.service;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    //create
    public User createUser(User user) {
        if (user.getCards() != null) {
            user.getCards().forEach(card -> card.setUser(user));
        }
        return userRepository.save(user);
    }

    //get by id
    public User findUserById(Long id) {
        //return userRepository.findById(id).orElse(null);
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User with id " + id + " not found!"));
    }

    //get all with pagination
    public Page<User> findAllUsers(int page, int size) {
        return userRepository.findAll(PageRequest.of(page, size));
    }

    // get by email
    public User getUserByEmailNamed(String email) {
        return userRepository.findByEmailNamed(email)
                .orElseThrow(() -> new EntityNotFoundException("User with email " + email + " not found!"));
    }

    // get by email JPQL
    public User getUserByEmailJPQl(String email) {
        return userRepository.findByEmailJPQL(email)
                .orElseThrow(() -> new EntityNotFoundException("User with email " + email + " not found!"));
    }

    // get by email Native
    public User getUserByEmailNative(String email) {
        return userRepository.findByEmailNativeQuery(email)
                .orElseThrow(() -> new EntityNotFoundException("User with email " + email + " not found!"));
    }

    //update
    public User updateUser(Long id, User updateUser) {
        User existUser = findUserById(id);
        existUser.setName(updateUser.getName());
        existUser.setSurname(updateUser.getSurname());
        existUser.setBirthDate(updateUser.getBirthDate());
        existUser.setCards(updateUser.getCards());
        existUser.setEmail(updateUser.getEmail());
        return userRepository.save(existUser);
    }

    //delete + каскадное удаление все CardInfo
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
