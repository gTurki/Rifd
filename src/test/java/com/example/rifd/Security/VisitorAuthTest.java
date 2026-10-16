package com.example.rifd.Security;

import com.example.rifd.Api.ApiException;
import com.example.rifd.DTOIn.VisitorLoginDTOIn;
import com.example.rifd.DTOIn.VisitorRegisterDTOIn;
import com.example.rifd.Entity.Visitor;
import com.example.rifd.Repository.VisitorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisitorAuthTest {

    private final VisitorRepository repo = mock(VisitorRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final JwtService jwt = mock(JwtService.class);
    private final VisitorAuthService service = new VisitorAuthService(repo, encoder, jwt);

    @Test
    void registerStoresLowerCaseEmailAndSignsIn() {
        when(repo.existsByEmail("sara@mail.com")).thenReturn(false);
        when(repo.saveAndFlush(any(Visitor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwt.issue(any(Visitor.class))).thenReturn("token");

        var out = service.register(new VisitorRegisterDTOIn(" Sara ", " Sara@Mail.com ", "password1"));
        assertEquals("sara@mail.com", out.email());
        assertEquals("Sara", out.name());
        assertEquals("token", out.token());
    }

    @Test
    void duplicateEmailIsRefused() {
        when(repo.existsByEmail("sara@mail.com")).thenReturn(true);
        assertThrows(ApiException.class, () -> service.register(new VisitorRegisterDTOIn("Sara", "SARA@mail.com", "password1")));
    }

    // wrong password and unknown email answer the same (no account enumeration)
    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() {
        Visitor v = new Visitor();
        v.setEmail("sara@mail.com");
        v.setPasswordHash(encoder.encode("password1"));
        when(repo.findByEmail("sara@mail.com")).thenReturn(Optional.of(v));
        when(repo.findByEmail("nobody@mail.com")).thenReturn(Optional.empty());

        String wrong = assertThrows(ApiException.class,
                () -> service.login(new VisitorLoginDTOIn("sara@mail.com", "nope"))).getMessage();
        String unknown = assertThrows(ApiException.class,
                () -> service.login(new VisitorLoginDTOIn("nobody@mail.com", "password1"))).getMessage();
        assertEquals(wrong, unknown);
    }
}
