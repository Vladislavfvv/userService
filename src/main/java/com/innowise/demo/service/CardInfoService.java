package com.innowise.demo.service;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.repository.CardInfoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CardInfoService {
    private final CardInfoRepository cardInfoRepository;

    public CardInfo save(CardInfo cardInfo) {
        return cardInfoRepository.save(cardInfo);
    }

    public CardInfo getCardInfoById(Long id) {
//        return cardInfoRepository.findById(id).orElse(null);
        return cardInfoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("CardInfo with id " + id + " not found"));
    }

    public Page<CardInfo> getAllCardInfos(int page, int size) {
        return cardInfoRepository.findAll(PageRequest.of(page, size));
    }

    public CardInfo updateCardInfo(Long id, CardInfo updateCardInfo) {
        CardInfo existCardInfo = getCardInfoById(id);
        existCardInfo.setNumber(updateCardInfo.getNumber());
        existCardInfo.setHolder(updateCardInfo.getHolder());
        existCardInfo.setExpirationDate(updateCardInfo.getExpirationDate());
        return cardInfoRepository.save(existCardInfo);
    }

    public void deleteCardInfo(Long id) {
        cardInfoRepository.deleteById(id);
    }
}
