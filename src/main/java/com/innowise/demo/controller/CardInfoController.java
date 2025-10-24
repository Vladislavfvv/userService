package com.innowise.demo.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.service.CardInfoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardInfoController {
    private final CardInfoService cardInfoService;

    @PostMapping
    public ResponseEntity<CardInfo> addCardInfo(@RequestBody CardInfo cardInfo) {
        return ResponseEntity.ok(cardInfoService.save(cardInfo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CardInfo> getCardInfoById(@PathVariable Long id) {
        return ResponseEntity.ok(cardInfoService.getCardInfoById(id));
    }

    @GetMapping
    public ResponseEntity<Page<CardInfo>> getAllCardInfos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(cardInfoService.getAllCardInfos(page, size));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CardInfo> updateCardInfo(@PathVariable Long id, @RequestBody CardInfo cardInfo) {
        return ResponseEntity.ok(cardInfoService.updateCardInfo(id, cardInfo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCardInfo(@PathVariable Long id) {
        cardInfoService.deleteCardInfo(id);
        return null;
    }
}
