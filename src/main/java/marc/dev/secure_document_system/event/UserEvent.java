package marc.dev.secure_document_system.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import marc.dev.secure_document_system.entity.UserEntity;
import marc.dev.secure_document_system.enumeration.EventType;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
public class UserEvent {
    private UserEntity user;
    private EventType type;
    private Map<?,?> data;
}
