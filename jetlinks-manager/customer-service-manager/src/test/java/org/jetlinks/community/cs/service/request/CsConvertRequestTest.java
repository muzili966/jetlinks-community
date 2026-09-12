package org.jetlinks.community.cs.service.request;

import org.hswebframework.web.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsConvertRequestTest {

    @Test
    void exactlyOneTargetIsRequired() {
        CsConvertRequest none = new CsConvertRequest();
        assertThrows(ValidationException.class, none::validate);

        CsConvertRequest both = new CsConvertRequest();
        both.setTenantId("t1");
        both.setNewTenantId("t2");
        assertThrows(ValidationException.class, both::validate);
    }

    @Test
    void linkOrCreateAlonePasses() {
        CsConvertRequest link = new CsConvertRequest();
        link.setTenantId("t1");
        assertDoesNotThrow(link::validate);

        CsConvertRequest create = new CsConvertRequest();
        create.setNewTenantId("t2");
        assertDoesNotThrow(create::validate);
    }
}
