package ru.mamakapa.ememeemail.services.jpa;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mamakapa.ememeemail.DTOs.requests.MessengerType;
import ru.mamakapa.ememeemail.entities.ImapEmail;
import ru.mamakapa.ememeemail.entities.jpa.BotUserEntity;
import ru.mamakapa.ememeemail.entities.jpa.ImapEmailEntity;
import ru.mamakapa.ememeemail.exceptions.BadRequestEmemeException;
import ru.mamakapa.ememeemail.exceptions.NotFoundEmemeException;
import ru.mamakapa.ememeemail.repositories.jpa.JpaBotUserRepository;
import ru.mamakapa.ememeemail.repositories.jpa.JpaImapEmailRepository;
import ru.mamakapa.ememeemail.services.ImapEmailService;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JpaImapEmailService implements ImapEmailService {

    final JpaImapEmailRepository emailRepository;
    final JpaBotUserRepository userRepository;

    public JpaImapEmailService(JpaImapEmailRepository emailRepository, JpaBotUserRepository userRepository) {
        this.emailRepository = emailRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public List<ImapEmail> getAllEmailsForChatId(Long chatId, MessengerType messengerType) {
        var res = findUserByChatIdAndTypeOrThrowException(chatId, messengerType);
        return res.getEmails().stream()
                .map(this::getImapEmailFromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ImapEmail add(Long chatId, MessengerType messengerType, String email, String password, String host) {
        var user = findUserByChatIdAndTypeOrThrowException(chatId, messengerType);
        var emailToSave = emailRepository.findByAddress(email).orElseGet( () ->
                emailRepository.save(new ImapEmailEntity(email, password, host,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now())))
        );
        if (user.getEmails().contains(emailToSave))
            throw new BadRequestEmemeException("Email = " + email + " was already subscribed by user with id = " + chatId);

        user.getEmails().add(emailToSave);
        userRepository.save(user);

        return getImapEmailFromEntity(emailToSave);
    }

    @Override
    @Transactional
    public ImapEmail remove(Long chatId, MessengerType messengerType, String email) {
        var user = findUserByChatIdAndTypeOrThrowException(chatId, messengerType);
        var emailToDelete = emailRepository.findByAddress(email).orElseThrow(() ->
                new NotFoundEmemeException("Email with address " + email + " does not subscribed"));

        user.getEmails().remove(emailToDelete);
        emailToDelete.getUsers().remove(user);
        userRepository.save(user);
        emailRepository.save(emailToDelete);

        if (emailToDelete.getUsers().isEmpty()){
            emailRepository.delete(emailToDelete);
        }

        return getImapEmailFromEntity(emailToDelete);
    }

    @Override
    @Transactional
    public void patch(ImapEmail emailWithUpdates) {
        var emailToUpdate = emailRepository.findById(emailWithUpdates.getId()).orElseThrow(() ->
                new NotFoundEmemeException("Email with address " + emailWithUpdates.getEmail() + " does not subscribed"));
        emailToUpdate.setAddress(emailWithUpdates.getEmail());
        emailToUpdate.setLastUpdated(emailWithUpdates.getLastMessageTime());
        emailToUpdate.setHost(emailWithUpdates.getHost());
        emailToUpdate.setPassword(emailWithUpdates.getAppPassword());
        emailToUpdate.setLastChecked(emailWithUpdates.getLastChecked());
    }

    private BotUserEntity findUserByChatIdAndTypeOrThrowException(Long chatId, MessengerType messengerType){
        return userRepository.findByChatIdAndType(chatId, messengerType)
                .orElseThrow(() -> new NotFoundEmemeException("User with chatId = " + chatId +
                        " and mesType = " + messengerType + " is not registered"));
    }

    private ImapEmail getImapEmailFromEntity(ImapEmailEntity entity){
        return ImapEmail.builder()
                .id(entity.getId())
                .appPassword(entity.getPassword())
                .host(entity.getHost())
                .email(entity.getAddress())
                .lastChecked(entity.getLastChecked())
                .lastMessageTime(entity.getLastUpdated())
                .build();
    }
}
