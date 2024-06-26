package com.whiteclarkegroup.liquibaselinter;

import com.google.common.collect.ImmutableSet;
import com.whiteclarkegroup.liquibaselinter.config.Config;
import com.whiteclarkegroup.liquibaselinter.config.rules.RuleRunner;
import com.whiteclarkegroup.liquibaselinter.report.ReportItem;
import com.whiteclarkegroup.liquibaselinter.resolvers.ChangeSetParameterResolver;
import com.whiteclarkegroup.liquibaselinter.resolvers.DefaultConfigParameterResolver;
import com.whiteclarkegroup.liquibaselinter.resolvers.RuleRunnerParameterResolver;
import liquibase.ContextExpression;
import liquibase.change.Change;
import liquibase.change.core.AddColumnChange;
import liquibase.change.core.InsertDataChange;
import liquibase.change.core.UpdateDataChange;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.exception.ChangeLogParseException;
import liquibase.precondition.core.PreconditionContainer;
import liquibase.precondition.core.SqlPrecondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;
import java.util.stream.Collectors;

import static com.whiteclarkegroup.liquibaselinter.report.ReportItem.ReportItemType.IGNORED;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith({ChangeSetParameterResolver.class, DefaultConfigParameterResolver.class, RuleRunnerParameterResolver.class})
class ChangeLogLinterTest {

    private ChangeLogLinter changeLogLinter;

    @BeforeEach
    void setUp() {
        changeLogLinter = new ChangeLogLinter();
    }

    @DisplayName("Should lint change sets with standard comment")
    @Test
    void shouldLintChangeSetsWithStandardComment() throws ChangeLogParseException {
        Config config = new Config.Builder().withFailFast(true).build();
        DatabaseChangeLog databaseChangeLog = mock(DatabaseChangeLog.class);
        ChangeSet changeSet = getChangeSet(databaseChangeLog, ImmutableSet.of("ddl_test"), "Test Data column");
        changeLogLinter.lintChangeLog(databaseChangeLog, config, new RuleRunner(config, new ArrayList<>(), new HashSet<>()));
        verify(changeSet, times(1)).getChanges();
    }


    @DisplayName("Should ignore rule violations for change sets with lint disabled comment")
    @Test
    void shouldIgnoreChangeSetsWithLintIgnoreComment(Config config, RuleRunner ruleRunner) throws ChangeLogParseException {
        DatabaseChangeLog databaseChangeLog = mock(DatabaseChangeLog.class);
        ChangeSet changeSet = getChangeSet(databaseChangeLog, ImmutableSet.of("ddl_test"), "comment includes lql-ignore");
        addChangeToChangeSet(changeSet, new AddColumnChange(), new AddColumnChange());

        changeLogLinter.lintChangeLog(databaseChangeLog, config, ruleRunner);
        List<ReportItem.ReportItemType> collect = ruleRunner.buildReport().getItems().stream().map(ReportItem::getType).collect(Collectors.toList());
        assertThat(collect).asList().contains(IGNORED);
    }

    @DisplayName("Should not fall over on null comment")
    @Test
    void shouldNotFallOverOnNullComment() throws ChangeLogParseException {
        Config config = new Config.Builder().withFailFast(true).build();
        DatabaseChangeLog databaseChangeLog = mock(DatabaseChangeLog.class);
        ChangeSet changeSet = getChangeSet(databaseChangeLog, ImmutableSet.of("ddl_test"), "Comment");
        changeLogLinter.lintChangeLog(databaseChangeLog, config, new RuleRunner(config, new ArrayList<>(), new HashSet<>()));
        verify(changeSet, times(1)).getChanges();
    }

    @DisplayName("Should not allow more than one ddl_test change in a change set")
    @Test
    void shouldNotAllowMoreThanOneDdlTestChangeInAChangeSet(Config config, RuleRunner ruleRunner) {
        DatabaseChangeLog databaseChangeLog = mock(DatabaseChangeLog.class);
        ChangeSet changeSet = getChangeSet(databaseChangeLog, ImmutableSet.of("ddl_test"), "Comment");
        addChangeToChangeSet(changeSet, new AddColumnChange(), new AddColumnChange());

        assertThatExceptionOfType(ChangeLogParseException.class)
            .isThrownBy(() -> changeLogLinter.lintChangeLog(databaseChangeLog, config, ruleRunner))
            .withMessageContaining("Should only have a single ddl change per change set");
    }

    @DisplayName("Should allow one ddl_test change in a change set")
    @Test
    void shouldAllowOneDdlTestChangeInAChangeSet(Config config, RuleRunner ruleRunner) {
        DatabaseChangeLog databaseChangeLog = mock(DatabaseChangeLog.class);
        ChangeSet changeSet = getChangeSet(databaseChangeLog, ImmutableSet.of("ddl_test"), "Comment");
        addChangeToChangeSet(changeSet, new AddColumnChange());

        assertThatNoException().isThrownBy(() -> changeLogLinter.lintChangeLog(databaseChangeLog, config, ruleRunner));
    }

    @DisplayName("Should not allow ddl_test changes in context other than ddl_test")
    @Test
    void shouldNotAllowDdlTestChangesInContextOtherThanDdlTest(Config config, RuleRunner ruleRunner) {
        DatabaseChangeLog databaseChangeLog = mock(DatabaseChangeLog.class);
        ChangeSet changeSet = getChangeSet(databaseChangeLog, ImmutableSet.of("dml_test"), "Comment");
        addChangeToChangeSet(changeSet, new AddColumnChange());

        assertThatExceptionOfType(ChangeLogParseException.class)
            .isThrownBy(() -> changeLogLinter.lintChangeLog(databaseChangeLog, config, ruleRunner))
            .withMessageContaining("Should have a ddl changes under ddl contexts");
    }

    @DisplayName("Should not allow dml changes in ddl_test context")
    @Test
    void shouldNotAllowDmlChangesInDdlTestContext(Config config, RuleRunner ruleRunner) {
        DatabaseChangeLog databaseChangeLog = mock(DatabaseChangeLog.class);
        ChangeSet changeSet = getChangeSet(databaseChangeLog, ImmutableSet.of("ddl_test"), "Comment");
        addChangeToChangeSet(changeSet, new InsertDataChange());

        assertThatExceptionOfType(ChangeLogParseException.class)
            .isThrownBy(() -> changeLogLinter.lintChangeLog(databaseChangeLog, config, ruleRunner))
            .withMessageContaining("Should have a ddl changes under ddl contexts");
    }

    @DisplayName("Should not allow spaces in filename - it causes issues on some platforms")
    @Test
    void shouldNotAllowSpacesInFilename(Config config, RuleRunner ruleRunner) {
        DatabaseChangeLog passingChangelog = mock(DatabaseChangeLog.class);
        when(passingChangelog.getFilePath()).thenReturn("modules/foo/nice.xml");

        DatabaseChangeLog failingChangelog = mock(DatabaseChangeLog.class);
        when(failingChangelog.getFilePath()).thenReturn("modules/foo/whoops space.xml");

        assertThatExceptionOfType(ChangeLogParseException.class)
            .isThrownBy(() -> changeLogLinter.lintChangeLog(failingChangelog, config, ruleRunner))
            .withMessageContaining("Changelog filenames should not contain spaces");
    }

    @DisplayName("Should not lint baseline script")
    @Test
    void shouldNotLintBaselineScript(Config config, RuleRunner ruleRunner) throws ChangeLogParseException {
        DatabaseChangeLog databaseChangeLog = mock(DatabaseChangeLog.class);
        ChangeSet changeSet = getChangeSet(databaseChangeLog, ImmutableSet.of("baseline_ddl_test"), "Test Data column");
        changeLogLinter.lintChangeLog(databaseChangeLog, config, ruleRunner);
        verify(changeSet, never()).getChanges();
    }

    @DisplayName("Should not lint script matching ignore file pattern")
    @Test
    void shouldNotLintScriptMatchingIgnoreFilePattern(Config config, RuleRunner ruleRunner) throws ChangeLogParseException {
        DatabaseChangeLog databaseChangeLog = mock(DatabaseChangeLog.class);
        ChangeSet changeSet = getChangeSet(databaseChangeLog, ImmutableSet.of("baseline_ddl_test"), "Test Data column");
        when(changeSet.getFilePath()).thenReturn("/ignore/core/TEST.xml");
        changeLogLinter.lintChangeLog(databaseChangeLog, config, ruleRunner);
        verify(changeSet, never()).getChanges();
    }

    @DisplayName("Should not allow change set without a comment")
    @Test
    void shouldNotAllowChangeLogWithoutComment(Config config, RuleRunner ruleRunner) {
        DatabaseChangeLog databaseChangeLog = mock(DatabaseChangeLog.class);
        getChangeSet(databaseChangeLog, ImmutableSet.of("dml"), null);

        assertThatExceptionOfType(ChangeLogParseException.class)
            .isThrownBy(() -> changeLogLinter.lintChangeLog(databaseChangeLog, config, ruleRunner))
            .withMessageContaining("Change set must have a comment");
    }

    @DisplayName("Should not allow context with suffix not ending in _test  or _script")
    @Test
    void shouldNotAllowContextWithSuffixNotEndingInAllowed(Config config, RuleRunner ruleRunner) {
        DatabaseChangeLog databaseChangeLog = mock(DatabaseChangeLog.class);
        ChangeSet changeSet = getChangeSet(databaseChangeLog, ImmutableSet.of("dml"), "Comment");
        addChangeToChangeSet(changeSet, new UpdateDataChange());

        assertThatExceptionOfType(ChangeLogParseException.class)
            .isThrownBy(() -> changeLogLinter.lintChangeLog(databaseChangeLog, config, ruleRunner))
            .withMessageContaining("Context is incorrect, should end with '_test' or '_script'");
    }

    @Test
    void shouldPreventPrecondition(Config config, RuleRunner ruleRunner) {
        DatabaseChangeLog databaseChangeLog = mock(DatabaseChangeLog.class);
        ChangeSet changeSet = getChangeSet(databaseChangeLog, ImmutableSet.of("core_test"), "Comment");
        SqlPrecondition precondition = new SqlPrecondition();
        precondition.setSql("SELECT COUNT(*) FROM BAR");
        precondition.setExpectedResult("0");
        PreconditionContainer preconditionContainer = new PreconditionContainer();
        preconditionContainer.setOnFail("MARK_RAN");
        preconditionContainer.addNestedPrecondition(precondition);
        when(changeSet.getPreconditions()).thenReturn(preconditionContainer);
        addChangeToChangeSet(changeSet, new InsertDataChange());

        assertThatExceptionOfType(ChangeLogParseException.class)
            .isThrownBy(() -> changeLogLinter.lintChangeLog(databaseChangeLog, config, ruleRunner))
            .withMessageContaining("Preconditions are not allowed in this project");
    }

    @SafeVarargs
    private final <T extends Change> void addChangeToChangeSet(ChangeSet changeSet, T... changes) {
        when(changeSet.getChanges()).thenReturn(Arrays.asList(changes));
        for (T change : changes) {
            change.setChangeSet(changeSet);
        }
    }

    private ChangeSet getChangeSet(DatabaseChangeLog databaseChangeLog, Set<String> contexts, String comment) {
        ChangeSet changeSet = mock(ChangeSet.class);
        when(changeSet.getComments()).thenReturn(comment);
        if (contexts != null) {
            ContextExpression expression = mock(ContextExpression.class);
            when(changeSet.getContexts()).thenReturn(expression);
            when(expression.getContexts()).thenReturn(contexts);
        }
        when(databaseChangeLog.getChangeSets()).thenReturn(Collections.singletonList(changeSet));
        return changeSet;
    }

}
