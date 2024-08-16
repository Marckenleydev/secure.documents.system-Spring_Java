package marc.dev.secure_document_system.event.listener;

import lombok.RequiredArgsConstructor;
import marc.dev.secure_document_system.event.UserEvent;
import marc.dev.secure_document_system.service.EmailService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserEventListener {

    private final EmailService emailService;
    @EventListener
    public void onUserEvent(UserEvent event){
        switch (event.getType()){
            case REGISTRATION -> emailService.sendNewAccountEmail(event.getUser().getFirstName(), event.getUser().getEmail(), (String)event.getData().get("key"));
            case RESETPASSWORD -> emailService.sendPasswordResetEmail(event.getUser().getFirstName(), event.getUser().getEmail(), (String)event.getData().get("key"));
            default -> {}
        }
    }
}
