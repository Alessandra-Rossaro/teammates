package teammates.test.cases.logic;

import java.util.List;

import org.testng.annotations.Test;

import teammates.common.datatransfer.attributes.AccountAttributes;
import teammates.common.datatransfer.attributes.InstructorAttributes;
import teammates.common.datatransfer.attributes.StudentAttributes;
import teammates.common.datatransfer.attributes.StudentProfileAttributes;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.exception.InvalidParametersException;
import teammates.common.exception.JoinCourseException;
import teammates.common.util.Config;
import teammates.common.util.Const;
import teammates.common.util.FieldValidator;
import teammates.common.util.StringHelper;
import teammates.logic.api.Logic;
import teammates.logic.core.AccountsLogic;
import teammates.logic.core.InstructorsLogic;
import teammates.logic.core.ProfilesLogic;
import teammates.logic.core.StudentsLogic;
import teammates.storage.api.AccountsDb;
import teammates.test.driver.AssertHelper;
import teammates.test.driver.Priority;

/**
 * SUT: {@link AccountsLogic}.
 */
public class AccountsLogicTest extends BaseLogicTest {

    private static final AccountsLogic accountsLogic = AccountsLogic.inst();
    private static final ProfilesLogic profilesLogic = ProfilesLogic.inst();
    private static final InstructorsLogic instructorsLogic = InstructorsLogic.inst();
    private static final StudentsLogic studentsLogic = StudentsLogic.inst();
    private static final Logic logic = new Logic();

    @SuppressWarnings("deprecation")
    @Test
    public void testGetInstructorAccounts() throws Exception {

        ______TS("success case");

        List<AccountAttributes> instructorAccounts = logic.getInstructorAccounts();
        int size = instructorAccounts.size();

        accountsLogic.createAccount(
                AccountAttributes.builder()
                        .withGoogleId("test.account")
                        .withName("Test Account")
                        .withIsInstructor(true)
                        .withEmail("test@account.com")
                        .withInstitute("Foo University")
                        .build());
        instructorAccounts = logic.getInstructorAccounts();
        assertEquals(instructorAccounts.size(), size + 1);

        accountsLogic.deleteAccountCascade("test.account");
        instructorAccounts = logic.getInstructorAccounts();
        assertEquals(instructorAccounts.size(), size);
    }

    @Test
    public void testCreateAccount() throws Exception {

        ______TS("typical success case");

        AccountAttributes accountToCreate = AccountAttributes.builder()
                .withGoogleId("id")
                .withName("name")
                .withEmail("test@email.com")
                .withInstitute("dev")
                .withIsInstructor(true)
                .build();

        accountsLogic.createAccount(accountToCreate);
        verifyPresentInDatastore(accountToCreate);

        accountsLogic.deleteAccountCascade("id");

        ______TS("invalid parameters exception case");

        accountToCreate = AccountAttributes.builder()
                .withGoogleId("")
                .withName("name")
                .withEmail("test@email.com")
                .withInstitute("dev")
                .withIsInstructor(true)
                .build();
        AccountAttributes[] finalAccount = new AccountAttributes[] { accountToCreate };
        assertThrows(InvalidParametersException.class, () -> accountsLogic.createAccount(finalAccount[0]));

    }

    @Priority(-1)
    @Test
    public void testAccountFunctions() throws Exception {

        ______TS("test isAccountPresent");

        assertTrue(accountsLogic.isAccountPresent("idOfInstructor1OfCourse1"));
        assertTrue(accountsLogic.isAccountPresent("student1InCourse1"));

        assertFalse(accountsLogic.isAccountPresent("id-does-not-exist"));

        ______TS("test isAccountAnInstructor");

        assertTrue(accountsLogic.isAccountAnInstructor("idOfInstructor1OfCourse1"));

        assertFalse(accountsLogic.isAccountAnInstructor("student1InCourse1"));
        assertFalse(accountsLogic.isAccountAnInstructor("id-does-not-exist"));

        ______TS("test getInstructorAccounts");

        for (AccountAttributes aa : accountsLogic.getInstructorAccounts()) {
            ______TS(aa.toString());
        }

        assertEquals(14, accountsLogic.getInstructorAccounts().size());

        ______TS("test updateAccount");

        AccountAttributes expectedAccount = AccountAttributes.builder()
                .withGoogleId("idOfInstructor1OfCourse1")
                .withName("name")
                .withEmail("test2@email.com")
                .withInstitute("dev")
                .withIsInstructor(true)
                .build();

        accountsLogic.updateAccount(expectedAccount);
        AccountAttributes actualAccount = accountsLogic.getAccount(expectedAccount.googleId);
        expectedAccount.createdAt = actualAccount.createdAt;
        assertEquals(expectedAccount.toString(), actualAccount.toString());

        expectedAccount = AccountAttributes.builder()
                .withGoogleId("id-does-not-exist")
                .withName("name")
                .withEmail("test2@email.com")
                .withInstitute("dev")
                .withIsInstructor(true)
                .build();
        AccountAttributes finalAccount = expectedAccount;
        EntityDoesNotExistException ednee = assertThrows(EntityDoesNotExistException.class,
                () -> accountsLogic.updateAccount(finalAccount));
        AssertHelper.assertContains(AccountsDb.ERROR_UPDATE_NON_EXISTENT_ACCOUNT, ednee.getMessage());

        ______TS("test downgradeInstructorToStudentCascade");

        accountsLogic.downgradeInstructorToStudentCascade("idOfInstructor2OfCourse1");
        assertFalse(accountsLogic.isAccountAnInstructor("idOfInstructor2OfCourse1"));

        accountsLogic.downgradeInstructorToStudentCascade("student1InCourse1");
        assertFalse(accountsLogic.isAccountAnInstructor("student1InCourse1"));

        accountsLogic.downgradeInstructorToStudentCascade("id-does-not-exist");
        assertFalse(accountsLogic.isAccountPresent("id-does-not-exist"));

        ______TS("test makeAccountInstructor");

        accountsLogic.makeAccountInstructor("student2InCourse1");
        assertTrue(accountsLogic.isAccountAnInstructor("student2InCourse1"));
        accountsLogic.downgradeInstructorToStudentCascade("student2InCourse1");

        accountsLogic.makeAccountInstructor("id-does-not-exist");
        assertFalse(accountsLogic.isAccountPresent("id-does-not-exist"));

    }

    @Test
    public void testJoinCourseForStudent() throws Exception {

        String correctStudentId = "correctStudentId";
        String courseId = "idOfTypicalCourse1";
        String originalEmail = "original@email.com";

        // Create correct student with original@email.com
        StudentAttributes studentData = StudentAttributes
                .builder(courseId, "name", originalEmail)
                .withSection("sectionName")
                .withTeam("teamName")
                .withComments("")
                .build();
        studentsLogic.createStudentCascadeWithoutDocument(studentData);
        studentData = StudentsLogic.inst().getStudentForEmail(courseId,
                originalEmail);
        StudentAttributes finalStudent = studentData;

        verifyPresentInDatastore(studentData);

        ______TS("failure: wrong key");

        JoinCourseException jce = assertThrows(JoinCourseException.class,
                () -> accountsLogic.joinCourseForStudent(StringHelper.encrypt("wrongkey"), correctStudentId));
        assertEquals("You have used an invalid join link: %s", jce.getMessage());

        ______TS("failure: invalid parameters");

        InvalidParametersException ipe = assertThrows(InvalidParametersException.class,
                () -> accountsLogic.joinCourseForStudent(StringHelper.encrypt(finalStudent.key), "wrong student"));
        AssertHelper.assertContains(FieldValidator.REASON_INCORRECT_FORMAT, ipe.getMessage());

        ______TS("failure: googleID belongs to an existing student in the course");

        String existingId = "AccLogicT.existing.studentId";
        StudentAttributes existingStudent = StudentAttributes
                .builder(courseId, "name", "differentEmail@email.com")
                .withSection("sectionName")
                .withTeam("teamName")
                .withComments("")
                .withGoogleId(existingId)
                .build();
        studentsLogic.createStudentCascadeWithoutDocument(existingStudent);

        jce = assertThrows(JoinCourseException.class,
                () -> accountsLogic.joinCourseForStudent(StringHelper.encrypt(finalStudent.key), existingId));
        assertEquals(String.format(Const.StatusMessages.JOIN_COURSE_GOOGLE_ID_BELONGS_TO_DIFFERENT_USER, existingId),
                jce.getMessage());

        ______TS("success: without encryption and account already exists");

        AccountAttributes accountData = AccountAttributes.builder()
                .withGoogleId(correctStudentId)
                .withName("nameABC")
                .withEmail("real@gmail.com")
                .withInstitute("TEAMMATES Test Institute 1")
                .withIsInstructor(true)
                .build();

        accountsLogic.createAccount(accountData);
        accountsLogic.joinCourseForStudent(StringHelper.encrypt(studentData.key), correctStudentId);

        studentData.googleId = accountData.googleId;
        verifyPresentInDatastore(studentData);
        assertEquals(
                correctStudentId,
                logic.getStudentForEmail(studentData.course, studentData.email).googleId);

        ______TS("failure: already joined");

        jce = assertThrows(JoinCourseException.class,
                () -> accountsLogic.joinCourseForStudent(StringHelper.encrypt(finalStudent.key), correctStudentId));
        assertEquals("You (" + correctStudentId + ") have already joined this course",
                jce.getMessage());

        ______TS("failure: valid key belongs to a different user");

        jce = assertThrows(JoinCourseException.class,
                () -> accountsLogic.joinCourseForStudent(StringHelper.encrypt(finalStudent.key), "wrongstudent"));
        assertEquals("The join link used belongs to a different user whose "
                        + "Google ID is corre..dentId (only part of the Google ID is "
                        + "shown to protect privacy). If that Google ID is owned by you, "
                        + "please logout and re-login using that Google account. "
                        + "If it doesn’t belong to you, please "
                        + "<a href=\"mailto:" + Config.SUPPORT_EMAIL + "?"
                        + "body=Your name:%0AYour course:%0AYour university:\">"
                        + "contact us</a> so that we can investigate.",
                jce.getMessage());

        ______TS("success: with encryption and new account to be created");

        accountsLogic.deleteAccountCascade(correctStudentId);

        originalEmail = "email2@gmail.com";
        studentData = StudentAttributes
                .builder(courseId, "name", originalEmail)
                .withSection("sectionName")
                .withTeam("teamName")
                .withComments("")
                .build();
        studentsLogic.createStudentCascadeWithoutDocument(studentData);
        studentData = StudentsLogic.inst().getStudentForEmail(courseId,
                originalEmail);

        String encryptedKey = StringHelper.encrypt(studentData.key);
        accountsLogic.joinCourseForStudent(encryptedKey, correctStudentId);
        studentData.googleId = correctStudentId;
        verifyPresentInDatastore(studentData);
        assertEquals(correctStudentId,
                logic.getStudentForEmail(studentData.course, studentData.email).googleId);

        // check that we have the corresponding new account created.
        accountData.googleId = correctStudentId;
        accountData.email = originalEmail;
        accountData.name = "name";
        accountData.isInstructor = false;
        verifyPresentInDatastore(accountData);

        ______TS("success: join course as student does not revoke instructor status");

        // promote account to instructor
        accountsLogic.makeAccountInstructor(correctStudentId);

        // make the student 'unregistered' again
        studentData.googleId = "";
        studentsLogic.updateStudentCascadeWithoutDocument(studentData.email, studentData);
        assertEquals("",
                logic.getStudentForEmail(studentData.course, studentData.email).googleId);

        // rejoin
        logic.joinCourseForStudent(encryptedKey, correctStudentId);
        assertEquals(correctStudentId,
                logic.getStudentForEmail(studentData.course, studentData.email).googleId);

        // check if still instructor
        assertTrue(logic.isInstructor(correctStudentId));

        accountsLogic.deleteAccountCascade(correctStudentId);
        accountsLogic.deleteAccountCascade(existingId);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testJoinCourseForInstructor() throws Exception {

        InstructorAttributes instructor = dataBundle.instructors.get("instructorNotYetJoinCourse");
        String loggedInGoogleId = "AccLogicT.instr.id";
        String[] encryptedKey = new String[] {
                instructorsLogic.getEncryptedKeyForInstructor(instructor.courseId, instructor.email)
        };

        ______TS("failure: googleID belongs to an existing instructor in the course");

        JoinCourseException jce = assertThrows(JoinCourseException.class,
                () -> accountsLogic.joinCourseForInstructor(encryptedKey[0], "idOfInstructorWithOnlyOneSampleCourse"));
        assertEquals(
                String.format(Const.StatusMessages.JOIN_COURSE_GOOGLE_ID_BELONGS_TO_DIFFERENT_USER,
                        "idOfInstructorWithOnlyOneSampleCourse"),
                jce.getMessage());

        ______TS("success: instructor joined and new account be created");

        accountsLogic.joinCourseForInstructor(encryptedKey[0], loggedInGoogleId);

        InstructorAttributes joinedInstructor =
                instructorsLogic.getInstructorForEmail(instructor.courseId, instructor.email);
        assertEquals(loggedInGoogleId, joinedInstructor.googleId);

        AccountAttributes accountCreated = accountsLogic.getAccount(loggedInGoogleId);
        assertNotNull(accountCreated);

        ______TS("success: instructor joined but Account object creation goes wrong");

        //Delete account to simulate Account object creation goes wrong
        AccountsDb accountsDb = new AccountsDb();
        accountsDb.deleteAccount(loggedInGoogleId);

        //Try to join course again, Account object should be recreated
        accountsLogic.joinCourseForInstructor(encryptedKey[0], loggedInGoogleId);

        joinedInstructor = instructorsLogic.getInstructorForEmail(instructor.courseId, instructor.email);
        assertEquals(loggedInGoogleId, joinedInstructor.googleId);

        accountCreated = accountsLogic.getAccount(loggedInGoogleId);
        assertNotNull(accountCreated);

        accountsLogic.deleteAccountCascade(loggedInGoogleId);

        ______TS("success: instructor joined but account already exists");

        AccountAttributes nonInstrAccount = dataBundle.accounts.get("student1InCourse1");
        InstructorAttributes newIns = InstructorAttributes
                .builder(null, instructor.courseId, nonInstrAccount.name, nonInstrAccount.email)
                .build();

        instructorsLogic.createInstructor(newIns);
        encryptedKey[0] = instructorsLogic.getEncryptedKeyForInstructor(instructor.courseId, nonInstrAccount.email);
        assertFalse(accountsLogic.getAccount(nonInstrAccount.googleId).isInstructor);

        accountsLogic.joinCourseForInstructor(encryptedKey[0], nonInstrAccount.googleId);

        joinedInstructor = instructorsLogic.getInstructorForEmail(instructor.courseId, nonInstrAccount.email);
        assertEquals(nonInstrAccount.googleId, joinedInstructor.googleId);
        assertTrue(accountsLogic.getAccount(nonInstrAccount.googleId).isInstructor);
        instructorsLogic.verifyInstructorExists(nonInstrAccount.googleId);

        ______TS("success: instructor join and assigned institute when some instructors have not joined course");

        instructor = dataBundle.instructors.get("instructor4");
        newIns = InstructorAttributes
                .builder(null, instructor.courseId, "anInstructorWithoutGoogleId", "anInstructorWithoutGoogleId@gmail.com")
                .build();

        instructorsLogic.createInstructor(newIns);

        nonInstrAccount = dataBundle.accounts.get("student2InCourse1");
        nonInstrAccount.email = "newInstructor@gmail.com";
        nonInstrAccount.name = " newInstructor";
        nonInstrAccount.googleId = "newInstructorGoogleId";
        newIns = InstructorAttributes.builder(null, instructor.courseId, nonInstrAccount.name, nonInstrAccount.email)
                .build();

        instructorsLogic.createInstructor(newIns);
        encryptedKey[0] = instructorsLogic.getEncryptedKeyForInstructor(instructor.courseId, nonInstrAccount.email);

        accountsLogic.joinCourseForInstructor(encryptedKey[0], nonInstrAccount.googleId);

        joinedInstructor = instructorsLogic.getInstructorForEmail(instructor.courseId, nonInstrAccount.email);
        assertEquals(nonInstrAccount.googleId, joinedInstructor.googleId);
        instructorsLogic.verifyInstructorExists(nonInstrAccount.googleId);

        AccountAttributes instructorAccount = accountsLogic.getAccount(nonInstrAccount.googleId);
        assertEquals("TEAMMATES Test Institute 1", instructorAccount.institute);

        accountsLogic.deleteAccountCascade(nonInstrAccount.googleId);

        ______TS("failure: instructor already joined");

        nonInstrAccount = dataBundle.accounts.get("student1InCourse1");
        instructor = dataBundle.instructors.get("instructorNotYetJoinCourse");

        encryptedKey[0] = instructorsLogic.getEncryptedKeyForInstructor(instructor.courseId, nonInstrAccount.email);
        joinedInstructor = instructorsLogic.getInstructorForEmail(instructor.courseId, nonInstrAccount.email);
        InstructorAttributes[] finalInstructor = new InstructorAttributes[] { joinedInstructor };
        jce = assertThrows(JoinCourseException.class,
                () -> accountsLogic.joinCourseForInstructor(encryptedKey[0], finalInstructor[0].googleId));
        assertEquals(joinedInstructor.googleId + " has already joined this course",
                jce.getMessage());

        ______TS("failure: key belongs to a different user");

        jce = assertThrows(JoinCourseException.class,
                () -> accountsLogic.joinCourseForInstructor(encryptedKey[0], "otherUserId"));
        assertEquals("The join link used belongs to a different user whose "
                        + "Google ID is stude..ourse1 (only part of the Google ID is "
                        + "shown to protect privacy). If that Google ID is owned by you, "
                        + "please logout and re-login using that Google account. "
                        + "If it doesn’t belong to you, please "
                        + "<a href=\"mailto:" + Config.SUPPORT_EMAIL + "?"
                        + "body=Your name:%0AYour course:%0AYour university:\">"
                        + "contact us</a> so that we can investigate.",
                jce.getMessage());

        ______TS("failure: invalid key");
        String invalidKey = StringHelper.encrypt("invalidKey");

        jce = assertThrows(JoinCourseException.class,
                () -> accountsLogic.joinCourseForInstructor(invalidKey, loggedInGoogleId));
        assertEquals("You have used an invalid join link: /page/instructorCourseJoin?key=" + invalidKey,
                jce.getMessage());
    }

    @Test
    public void testDeleteAccountCascade() throws Exception {

        ______TS("typical success case");

        InstructorAttributes instructor = dataBundle.instructors.get("instructor5");
        AccountAttributes account = dataBundle.accounts.get("instructor5");
        // create a profile for the account
        StudentProfileAttributes studentProfile = StudentProfileAttributes.builder(account.googleId)
                .withShortName("Test")
                .build();
        profilesLogic.updateOrCreateStudentProfile(studentProfile);

        // Make instructor account id a student too.
        StudentAttributes student = StudentAttributes
                .builder(instructor.courseId, instructor.name, "email@com")
                .withSection("section")
                .withTeam("team")
                .withComments("")
                .withGoogleId(instructor.googleId)
                .build();
        studentsLogic.createStudentCascadeWithoutDocument(student);
        verifyPresentInDatastore(account);
        verifyPresentInDatastore(studentProfile);
        verifyPresentInDatastore(instructor);
        verifyPresentInDatastore(student);

        accountsLogic.deleteAccountCascade(instructor.googleId);

        verifyAbsentInDatastore(account);
        verifyAbsentInDatastore(studentProfile);
        verifyAbsentInDatastore(instructor);
        verifyAbsentInDatastore(student);
    }

    //TODO: add missing test cases
}
