package teammates.test.pageobjects;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.params.HttpParams;
import org.apache.http.ssl.SSLContexts;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.remote.UselessFileDetector;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import teammates.common.util.ThreadHelper;
import teammates.common.util.Url;
import teammates.test.driver.AssertHelper;
import teammates.test.driver.HtmlHelper;
import teammates.test.driver.TestProperties;
import teammates.test.util.FileHelper;

/**
 * An abstract class that represents a browser-loaded page of the app and
 * provides ways to interact with it. Also contains methods to validate some
 * aspects of the page. .e.g, html page source. <br>
 * 
 * Note: We are using the PageObjects pattern here.
 * https://code.google.com/p/selenium/wiki/PageObjects
 * 
 */
@SuppressWarnings("deprecation")
public abstract class AppPage {
    private static final By MAIN_CONTENT = By.id("mainContent");
    private static final int VERIFICATION_RETRY_COUNT = 5;
    private static final int VERIFICATION_RETRY_DELAY_IN_MS = 1000;
    
    /** Browser instance the page is loaded into */
    protected Browser browser;
    
    /** These are elements common to most pages in our app */
    @FindBy(id = "statusMessagesToUser")
    protected WebElement statusMessage;
    
    @FindBy(xpath = "//*[@id=\"contentLinks\"]/ul[1]/li[1]/a")
    protected WebElement instructorHomeTab;
    
    @FindBy(xpath = "//*[@id=\"contentLinks\"]/ul[1]/li[2]/a")
    protected WebElement instructorCoursesTab;
    
    @FindBy(xpath = "//*[@id=\"contentLinks\"]/ul[1]/li[4]/a")
    protected WebElement instructorStudentsTab;
    
    @FindBy(xpath = "//*[@id=\"contentLinks\"]/ul[1]/li[5]/a")
    protected WebElement instructorCommentsTab;
    
    @FindBy(xpath = "//*[@id=\"contentLinks\"]/ul[1]/li[7]/a")
    protected WebElement instructorHelpTab;
    
    @FindBy(id = "studentHomeNavLink")
    protected WebElement studentHomeTab;
    
    @FindBy(id = "studentProfileNavLink")
    protected WebElement studentProfileTab;
    
    @FindBy(id = "studentCommentsNavLink")
    protected WebElement studentCommentsTab;
    
    @FindBy(id = "studentHelpLink")
    protected WebElement studentHelpTab;
    
    @FindBy(id = "btnLogout")
    protected WebElement logoutButton;
    
    /**
     * Used by subclasses to create a {@code AppPage} object to wrap around the
     * given {@code browser} object. Fails if the page content does not match
     * the page type, as defined by the sub-class.
     */
    public AppPage(Browser browser) {
        this.browser = browser;
        boolean isCorrectPageType = containsExpectedPageContents();
        
        if (isCorrectPageType) {
            return;
        }
        
        // To minimize test failures due to eventual consistency, we try to
        //  reload the page and compare once more.
        System.out.println("#### Incorrect page type: going to try reloading the page.");
        
        ThreadHelper.waitFor(2000);
        
        this.reloadPage();
        isCorrectPageType = containsExpectedPageContents();
        
        if (isCorrectPageType) {
            return;
        }
        
        System.out.println("######### Not in the correct page! ##########");
        throw new IllegalStateException("Not in the correct page!");
    }
    

    /**
     * Fails if the new page content does not match content expected in a page of
     * the type indicated by the parameter {@code typeOfPage}.
     */
    public static <T extends AppPage> T getNewPageInstance(Browser currentBrowser, Url url, Class<T> typeOfPage) {
        currentBrowser.driver.get(url.toAbsoluteString());
        return getNewPageInstance(currentBrowser, typeOfPage);
    }

    /**
     * Fails if the new page content does not match content expected in a page of
     * the type indicated by the parameter {@code typeOfPage}.
     */
    public static <T extends AppPage> T getNewPageInstance(Browser currentBrowser, Class<T> typeOfPage) {
        try {
            Constructor<T> constructor = typeOfPage.getConstructor(Browser.class);
            T page = constructor.newInstance(currentBrowser);
            PageFactory.initElements(currentBrowser.driver, page);
            return page;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Gives an AppPage instance based on the given Browser.
     */
    public static AppPage getNewPageInstance(Browser currentBrowser) {
        return getNewPageInstance(currentBrowser, GenericAppPage.class);
    }

    /**
     * Simply loads the given URL.
     */
    public AppPage navigateTo(Url url) {
        browser.driver.get(url.toAbsoluteString());
        return this;
    }

    /**
     * Fails if the new page content does not match content expected in a page of
     * the type indicated by the parameter {@code newPageType}.
     */
    public <T extends AppPage> T changePageType(Class<T> newPageType) {
        return getNewPageInstance(browser, newPageType);
    }

    /**
     * Gives a {@link LoginPage} instance based on the given {@link Browser} and test configuration.
     * Fails if the page content does not match the content of the expected login page.
     */
    public static LoginPage createCorrectLoginPageType(Browser browser) {
        return getNewPageInstance(browser, TestProperties.isDevServer() ? DevServerLoginPage.class
                                                                        : GoogleLoginPage.class);
    }
    
    /**
     * Waits until the page is fully loaded.
     */
    public void waitForPageToLoad() {
        WebDriverWait wait = new WebDriverWait(browser.driver, TestProperties.TEST_TIMEOUT);
        wait.until(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver d) {
                // Check https://developer.mozilla.org/en/docs/web/api/document/readystate
                // to understand more on a web document's readyState
                return "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState"));
            }
        });
    }
    
    /**
     * Waits until the element is not covered by any other element.
     */
    public void waitForElementNotCovered(final WebElement element) {
        WebDriverWait wait = new WebDriverWait(browser.driver, TestProperties.TEST_TIMEOUT);
        wait.until(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver d) {
                return !isElementCovered(element);
            }
        });
    }
    
    public void waitForElementVisibility(WebElement element) {
        WebDriverWait wait = new WebDriverWait(browser.driver, TestProperties.TEST_TIMEOUT);
        wait.until(ExpectedConditions.visibilityOf(element));
    }
    
    public void waitForElementToBeClickable(WebElement element) {
        WebDriverWait wait = new WebDriverWait(browser.driver, TestProperties.TEST_TIMEOUT);
        wait.until(ExpectedConditions.elementToBeClickable(element));
    }

    public void waitForElementsVisibility(List<WebElement> elements) {
        WebDriverWait wait = new WebDriverWait(browser.driver, TestProperties.TEST_TIMEOUT);
        wait.until(ExpectedConditions.visibilityOfAllElements(elements));
    }

    /**
     * Waits for element to be invisible or not present, or timeout.
     */
    public void waitForElementToDisappear(By by) {
        WebDriverWait wait = new WebDriverWait(browser.driver, TestProperties.TEST_TIMEOUT);
        wait.until(ExpectedConditions.invisibilityOfElementLocated(by));
    }
    
    /**
     * Waits for a list of elements to be invisible or not present, or timeout.
     */
    public void waitForElementsToDisappear(List<WebElement> elements) {
        WebDriverWait wait = new WebDriverWait(browser.driver, TestProperties.TEST_TIMEOUT);
        wait.until(invisibilityOfAllElements(elements));
    }
    
    /**
     * Code adapted from SeleniumHQ's GitHub page.
     * TODO to be removed when Selenium is upgraded to the version supporting this method.
     *
     * An expectation for checking all elements from given list to be invisible
     *
     * @param elements used to check their invisibility
     * @return Boolean true when all elements are not visible anymore
     */
    private ExpectedCondition<Boolean> invisibilityOfAllElements(final List<WebElement> elements) {
        return new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver webDriver) {
                for (WebElement element : elements) {
                    try {
                        if (element.isDisplayed()) {
                            return false;
                        }
                    } catch (Exception e) {
                        // empty exception block as specified by Selenium's code
                    }
                }
                return true;
            }

            @Override
            public String toString() {
                return "invisibility of all elements " + elements;
            }
        };
    }

    /**
     * Waits for an alert to appear on the page, up to the timeout specified.
     */
    public void waitForAlertPresence() {
        WebDriverWait wait = new WebDriverWait(browser.driver, TestProperties.TEST_TIMEOUT);
        wait.until(ExpectedConditions.alertIsPresent());
    }

    /**
     * Waits for an alert modal to appear and dismisses it
     */
    public void waitForAndDismissAlertModal() {
        waitForConfirmationModalAndClickOk();
    }

    /**
     * Waits for a confirmation modal to appear and click the confirm button
     */
    public void waitForConfirmationModalAndClickOk() {
        waitForModalPresence();
        WebElement okayButton = browser.driver.findElement(By.className("modal-btn-ok"));
        waitForElementToBeClickable(okayButton);
        okayButton.click();
        waitForModalToDisappear();
    }

    /**
     * Waits for a confirmation modal to appear and click the cancel button
     */
    public void waitForConfirmationModalAndClickCancel() {
        waitForModalPresence();
        WebElement cancelButton = browser.driver.findElement(By.className("modal-btn-cancel"));
        waitForElementToBeClickable(cancelButton);
        cancelButton.click();
        waitForModalToDisappear();
    }

    private void waitForModalPresence() {
        WebElement closeButton = browser.driver.findElement(By.className("bootbox-close-button"));
        waitForElementToBeClickable(closeButton);
    }

    private void waitForModalToDisappear() {
        By modalBackdrop = By.className("modal-backdrop");
        waitForElementToDisappear(modalBackdrop);
    }

    /**
     * Waits for the element to appear in the page, up to the timeout specified.
     */
    public void waitForElementPresence(By by) {
        WebDriverWait wait = new WebDriverWait(browser.driver, TestProperties.TEST_TIMEOUT);
        wait.until(ExpectedConditions.presenceOfElementLocated(by));
    }

    /**
     * Waits for text contained in the element to appear in the page, or timeout
     */
    public void waitForTextContainedInElementPresence(By by, String text) {
        WebDriverWait wait = new WebDriverWait(browser.driver, TestProperties.TEST_TIMEOUT);
        wait.until(ExpectedConditions.textToBePresentInElementLocated(by, text));
    }
    
    /**
     * Waits for text contained in the element to disappear from the page, or timeout
     */
    public void waitForTextContainedInElementAbsence(By by, String text) {
        WebDriverWait wait = new WebDriverWait(browser.driver, TestProperties.TEST_TIMEOUT);
        wait.until(ExpectedConditions.not(ExpectedConditions.textToBePresentInElementLocated(by, text)));
    }
    
    /**
     * Switches to the new browser window just opened.
     */
    protected void switchToNewWindow() {
        browser.switchToNewWindow();
    }
    
    public void closeCurrentWindowAndSwitchToParentWindow() {
        browser.closeCurrentWindowAndSwitchToParentWindow();
    }
    
    public void reloadPage() {
        browser.driver.get(browser.driver.getCurrentUrl());
        waitForPageToLoad();
    }

    /** Equivalent to pressing the 'back' button of the browser. <br>
     * Fails if the page content does not match content expected in a page of
     * the type indicated by the parameter {@code typeOfPreviousPage}.
     */
    public <T extends AppPage> T goToPreviousPage(Class<T> typeOfPreviousPage) {
        browser.driver.navigate().back();
        waitForPageToLoad();
        return changePageType(typeOfPreviousPage);
    }

    /**
     * Equivalent to clicking the 'Courses' tab on the top menu of the page.
     * @return the loaded page.
     */
    public InstructorCoursesPage loadCoursesTab() {
        instructorCoursesTab.click();
        waitForPageToLoad();
        return changePageType(InstructorCoursesPage.class);
    }
    
    /**
     * Equivalent to clicking the 'Students' tab on the top menu of the page.
     * @return the loaded page.
     */
    public InstructorStudentListPage loadStudentsTab() {
        instructorStudentsTab.click();
        waitForPageToLoad();
        return changePageType(InstructorStudentListPage.class);
    }
    
    
    /**
     * Equivalent to clicking the 'Home' tab on the top menu of the page.
     * @return the loaded page.
     */
    public InstructorHomePage loadInstructorHomeTab() {
        instructorHomeTab.click();
        waitForPageToLoad();
        return changePageType(InstructorHomePage.class);
    }
    
    /**
     * Equivalent to clicking the 'Help' tab on the top menu of the page.
     * @return the loaded page.
     */
    public InstructorHelpPage loadInstructorHelpTab() {
        instructorHelpTab.click();
        waitForPageToLoad();
        switchToNewWindow();
        return changePageType(InstructorHelpPage.class);
    }
    
    /**
     * Equivalent to clicking the 'Comments' tab on the top menu of the page.
     * @return the loaded page.
     */
    public InstructorCommentsPage loadInstructorCommentsTab() {
        instructorCommentsTab.click();
        waitForPageToLoad();
        return changePageType(InstructorCommentsPage.class);
    }

    /**
     * Equivalent of clicking the 'Profile' tab on the top menu of the page.
     * @return the loaded page
     */
    public StudentProfilePage loadProfileTab() {
        studentProfileTab.click();
        waitForPageToLoad();
        return changePageType(StudentProfilePage.class);
    }
    
    /**
     * Equivalent of student clicking the 'Home' tab on the top menu of the page.
     * @return the loaded page
     */
    public StudentHomePage loadStudentHomeTab() {
        studentHomeTab.click();
        waitForPageToLoad();
        return changePageType(StudentHomePage.class);
    }
    
    /**
     * Equivalent of student clicking the 'Comments' tab on the top menu of the page.
     * @return the loaded page
     */
    public StudentCommentsPage loadStudentCommentsTab() {
        studentCommentsTab.click();
        waitForPageToLoad();
        return changePageType(StudentCommentsPage.class);
    }

    /**
     * Equivalent of clicking the 'Help' tab on the top menu of the page.
     * @return the loaded page
     */
    public StudentHelpPage loadStudentHelpTab() {
        studentHelpTab.click();
        waitForPageToLoad();
        switchToNewWindow();
        return changePageType(StudentHelpPage.class);
    }
    
    /**
     * Click the 'logout' link in the top menu of the page.
     */
    public AppPage logout() {
        logoutButton.click();
        return this;
    }
    
    /**
     * @return the HTML source of the currently loaded page.
     */
    public String getPageSource() {
        return browser.driver.getPageSource();
    }

    public void click(By by) {
        WebElement element = browser.driver.findElement(by);
        element.click();
    }
    
    public String getElementAttribute(By locator, String attrName) {
        return browser.driver.findElement(locator).getAttribute(attrName);
    }
    
    protected void fillTextBox(WebElement textBoxElement, String value) {
        textBoxElement.click();
        textBoxElement.clear();
        textBoxElement.sendKeys(value + Keys.TAB + Keys.TAB + Keys.TAB);
    }
    
    protected void fillFileBox(RemoteWebElement fileBoxElement, String fileName) {
        if (fileName.isEmpty()) {
            fileBoxElement.clear();
        } else {
            fileBoxElement.setFileDetector(new UselessFileDetector());
            String newFilePath = new File(fileName).getAbsolutePath();
            fileBoxElement.sendKeys(newFilePath);
        }
    }

    protected String getTextBoxValue(WebElement textBox) {
        return textBox.getAttribute("value");
    }

    /** 
     * 'check' the check box, if it is not already 'checked'.
     * No action taken if it is already 'checked'.
     */
    protected void markCheckBoxAsChecked(WebElement checkBox) {
        waitForElementVisibility(checkBox);
        if (!checkBox.isSelected()) {
            checkBox.click();
        }
    }

    /** 
     * 'uncheck' the check box, if it is already 'checked'.
     * No action taken if it is not already 'checked'.
     */
    protected void markCheckBoxAsUnchecked(WebElement checkBox) {
        if (checkBox.isSelected()) {
            checkBox.click();
        }
    }

    /** 
     * 'check' the radio button, if it is not already 'checked'.
     * No action taken if it is already 'checked'.
     */
    protected void markRadioButtonAsChecked(WebElement radioButton) {
        waitForElementVisibility(radioButton);
        if (!radioButton.isSelected()) {
            radioButton.click();
        }
    }
    
    /** 
     * Selection is based on the value shown to the user.
     * Since selecting an option by clicking on the option doesn't work sometimes
     * in Firefox, we simulate a user typing the value to select the option
     * instead (i.e., we use the {@code sendKeys()} method). <br>
     * <br>
     * The method will fail with an AssertionError if the selected value is
     * not the one we wanted to select.
     */
    public void selectDropdownByVisibleValue(WebElement element, String value) {
        Select select = new Select(element);
        select.selectByVisibleText(value);
        String selectedVisibleValue = select.getFirstSelectedOption().getText();
        assertEquals(value, selectedVisibleValue);
        element.sendKeys(Keys.RETURN);
    }
    
    /** 
     * Selection is based on the actual value.
     * Since selecting an option by clicking on the option doesn't work sometimes
     * in Firefox, we simulate a user typing the value to select the option
     * instead (i.e., we use the {@code sendKeys()} method). <br>
     * <br>
     * The method will fail with an AssertionError if the selected value is
     * not the one we wanted to select.
     */
    public void selectDropdownByActualValue(WebElement element, String value) {
        Select select = new Select(element);
        select.selectByValue(value);
        String selectedVisibleValue = select.getFirstSelectedOption().getAttribute("value");
        assertEquals(value, selectedVisibleValue);
        element.sendKeys(Keys.RETURN);
    }

    /**
     * @return the status message in the page. Returns "" if there is no
     * status message in the page.
     */
    public String getStatus() {
        return statusMessage == null ? "" : statusMessage.getText();
    }

    /** 
     * @return the value of the cell located at {@code (row,column)}
     * from the first table (which is of type {@code class=table}) in the page.
     */
    public String getCellValueFromDataTable(int row, int column) {
        return getCellValueFromDataTable(0, row, column);
    }
    
    /** 
     * @return the value of the cell located at {@code (row,column)}
     * from the nth(0-index-based) table (which is of type {@code class=table}) in the page.
     */
    public String getCellValueFromDataTable(int tableNum, int row, int column) {
        WebElement tableElement = browser.driver.findElements(By.className("table")).get(tableNum);
        WebElement trElement = tableElement.findElements(By.tagName("tr")).get(row);
        WebElement tdElement = trElement.findElements(By.tagName("td")).get(column);
        return tdElement.getText();
    }
    
    /** 
     * @return the value of the header located at {@code (row,column)}
     * from the nth(0-index-based) table (which is of type {@code class=table}) in the page.
     */
    public String getHeaderValueFromDataTable(int tableNum, int row, int column) {
        WebElement tableElement = browser.driver.findElements(By.className("table")).get(tableNum);
        WebElement trElement = tableElement.findElements(By.tagName("tr")).get(row);
        WebElement tdElement = trElement.findElements(By.tagName("th")).get(column);
        return tdElement.getText();
    }
    
    /** 
     * @return the number of rows from the nth(0-index-based) table
     * (which is of type {@code class=table}) in the page.
     */
    public int getNumberOfRowsFromDataTable(int tableNum) {
        WebElement tableElement = browser.driver.findElements(By.className("table")).get(tableNum);
        return tableElement.findElements(By.tagName("tr")).size();
    }
    
    /** 
     * @return the number of columns from the header in the table
     * (which is of type {@code class=table}) in the page.
     */
    public int getNumberOfColumnsFromDataTable(int tableNum) {
        WebElement tableElement = browser.driver.findElements(By.className("table")).get(tableNum);
        WebElement trElement = tableElement.findElement(By.tagName("tr"));
        return trElement.findElements(By.tagName("th")).size();
    }
    
    /** 
     * @return the id of the table
     * (which is of type {@code class=table}) in the page.
     */
    public String getDataTableId(int tableNum) {
        WebElement tableElement = browser.driver.findElements(By.className("table")).get(tableNum);
        return tableElement.getAttribute("id");
    }
    
    /**
     * Clicks the element and clicks 'Yes' in the follow up dialog box.
     * Fails if there is no dialog box.
     * @return the resulting page.
     */
    public AppPage clickAndConfirm(WebElement elementToClick) {
        respondToAlertWithRetry(elementToClick, true);
        waitForPageToLoad();
        return this;
    }
    
    /**
     * Clicks the hidden element and clicks 'Yes' in the follow up dialog box.
     * Fails if there is no dialog box.
     * @return the resulting page.
     */
    public AppPage clickHiddenElementAndConfirm(String elementId) {
        respondToAlertWithRetryForHiddenElement(elementId, true);
        waitForPageToLoad();
        return this;
    }
    
    /**
     * Clicks the element and clicks 'No' in the follow up dialog box.
     * Fails if there is no dialog box.
     * @return the resulting page.
     */
    public void clickAndCancel(WebElement elementToClick) {
        respondToAlertWithRetry(elementToClick, false);
        waitForPageToLoad();
    }
    
    /**
     * Clicks the hidden element and clicks 'No' in the follow up dialog box.
     * Fails if there is no dialog box.
     * @return the resulting page.
     */
    public void clickHiddenElementAndCancel(String elementId) {
        respondToAlertWithRetryForHiddenElement(elementId, false);
        waitForPageToLoad();
    }

    /** @return True if the page contains some basic elements expected in a page of the
     * specific type. e.g., the top heading.
     */
    protected abstract boolean containsExpectedPageContents();

    /**
     * @return True if there is a corresponding element for the given locator.
     */
    public boolean isElementPresent(By by) {
        return browser.driver.findElements(by).size() != 0;
    }
    
    /**
     * @return True if there is a corresponding element for the given id or name.
     */
    public boolean isElementPresent(String elementId) {
        try {
            browser.driver.findElement(By.id(elementId));
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }
    
    public boolean isElementVisible(String elementId) {
        try {
            return browser.driver.findElement(By.id(elementId)).isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    public boolean isElementVisible(By by) {
        try {
            return browser.driver.findElement(by).isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }
    
    public boolean isNamedElementVisible(String elementName) {
        try {
            return browser.driver.findElement(By.name(elementName)).isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }
    
    public boolean isElementEnabled(String elementId) {
        try {
            return browser.driver.findElement(By.id(elementId)).isEnabled();
        } catch (NoSuchElementException e) {
            return false;
        }
    }
    
    public boolean isNamedElementEnabled(String elementName) {
        try {
            return browser.driver.findElement(By.name(elementName)).isEnabled();
        } catch (NoSuchElementException e) {
            return false;
        }
    }
    
    public boolean isElementSelected(String elementId) {
        try {
            return browser.driver.findElement(By.id(elementId)).isSelected();
        } catch (NoSuchElementException e) {
            return false;
        }
    }
    
    /**
     * Checks if the midpoint of an element is covered by any other element.
     * @param element
     * @return true if element is covered, false otherwise.
     */
    public boolean isElementCovered(WebElement element) {
        int x = element.getLocation().x + element.getSize().width / 2;
        int y = element.getLocation().y + element.getSize().height / 2;
        JavascriptExecutor js = (JavascriptExecutor) browser.driver;
        WebElement topElem = (WebElement) js.executeScript("return document.elementFromPoint(" + x + "," + y + ");");
        return !topElem.equals(element);
    }

    public void verifyUnclickable(WebElement element) {
        assertNotNull(element.getAttribute("disabled"));
    }

    /**
     * Compares selected column's rows with patternString to check the order of rows.
     * This can be useful in checking if the table is sorted in a particular order.
     * Separate rows using {*}
     * e.g., {@code "value 1{*}value 2{*}value 3" }
     * The header row will be ignored
     */
    public void verifyTablePattern(int column, String patternString) {
        verifyTablePattern(0, column, patternString);
    }
    
    /**
     * Compares selected column's rows with patternString to check the order of rows.
     * This can be useful in checking if the table is sorted in a particular order.
     * Separate rows using {*}
     * e.g., {@code "value 1{*}value 2{*}value 3" }
     * The header row will be ignored
     */
    public void verifyTablePattern(int tableNum, int column, String patternString) {
        String[] splitString = patternString.split(java.util.regex.Pattern.quote("{*}"));
        int expectedNumberOfRowsInTable = splitString.length + 1;
        assertEquals(expectedNumberOfRowsInTable, getNumberOfRowsFromDataTable(tableNum));
        for (int row = 1; row < splitString.length; row++) {
            String tableCellString = this.getCellValueFromDataTable(tableNum, row, column);
            assertEquals(splitString[row - 1], tableCellString);
        }
    }
    
    /**
     * Verifies that the currently loaded page has the same HTML content as
     * the content given in the file at {@code filePath}. <br>
     * The HTML is checked for logical equivalence, not text equivalence.
     * @param filePath If this starts with "/" (e.g., "/expected.html"), the
     * folder is assumed to be {@link TestProperties.TEST_PAGES_FOLDER}.
     * @return The page (for chaining method calls).
     */
    public AppPage verifyHtml(String filePath) throws IOException {
        return verifyHtmlPart(null, filePath);
    }

    /**
     * Verifies that element specified in currently loaded page has the same HTML content as
     * the content given in the file at {@code filePath}. <br>
     * The HTML is checked for logical equivalence, not text equivalence.
     * @param filePathParam If this starts with "/" (e.g., "/expected.html"), the
     * folder is assumed to be {@link TestProperties.TEST_PAGES_FOLDER}.
     * @return The page (for chaining method calls).
     */
    public AppPage verifyHtmlPart(By by, String filePathParam) throws IOException {
        String filePath = (filePathParam.startsWith("/") ? TestProperties.TEST_PAGES_FOLDER : "") + filePathParam;
        boolean isPart = by != null;
        String actual = getPageSource(by);
        try {
            String expected = FileHelper.readFile(filePath);
            expected = HtmlHelper.injectTestProperties(expected);
            
            // The check is done multiple times with waiting times in between to account for
            // certain elements to finish loading (e.g ajax load, panel collapsing/expanding).
            for (int i = 0; i < VERIFICATION_RETRY_COUNT; i++) {
                if (i == VERIFICATION_RETRY_COUNT - 1) {
                    // Last retry count: do one last attempt and if it still fails,
                    // throw assertion error and show the differences
                    HtmlHelper.assertSameHtml(expected, actual, isPart);
                    break;
                }
                if (HtmlHelper.areSameHtml(expected, actual, isPart)) {
                    break;
                }
                ThreadHelper.waitFor(VERIFICATION_RETRY_DELAY_IN_MS);
                actual = getPageSource(by);
            }
            
        } catch (IOException | AssertionError e) {
            if (!testAndRunGodMode(filePath, actual, isPart)) {
                throw e;
            }
        }
        
        return this;
    }

    private String getPageSource(By by) {
        waitForAjaxLoaderGifToDisappear();
        String actual = by == null ? browser.driver.findElement(By.tagName("html")).getAttribute("innerHTML")
                                   : browser.driver.findElement(by).getAttribute("outerHTML");
        return HtmlHelper.processPageSourceForHtmlComparison(actual);
    }

    private boolean testAndRunGodMode(String filePath, String content, boolean isPart) throws IOException {
        return Boolean.parseBoolean(System.getProperty("godmode"))
                && regenerateHtmlFile(filePath, content, isPart);
    }
    
    private boolean regenerateHtmlFile(String filePath, String content, boolean isPart) throws IOException {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        TestProperties.verifyReadyForGodMode();
        String processedPageSource = HtmlHelper.processPageSourceForExpectedHtmlRegeneration(content, isPart);
        FileHelper.saveFile(filePath, processedPageSource);
        return true;
    }
    
    /**
     * Verifies that main content specified id "mainContent" in currently
     * loaded page has the same HTML content as
     * the content given in the file at {@code filePath}. <br>
     * The HTML is checked for logical equivalence, not text equivalence.
     * @param filePath If this starts with "/" (e.g., "/expected.html"), the
     * folder is assumed to be {@link TestProperties.TEST_PAGES_FOLDER}.
     * @return The page (for chaining method calls).
     */
    public AppPage verifyHtmlMainContent(String filePath) throws IOException {
        return verifyHtmlPart(MAIN_CONTENT, filePath);
    }
    
    /**
     * Verifies that the title of the loaded page is the same as {@code expectedTitle}
     * @param expectedTitle
     */
    public void verifyTitle(String expectedTitle) {
        assertEquals(expectedTitle, browser.driver.getTitle());
    }

    /**
     * Also supports the expression "{*}" which will match any text.
     * e.g. "team 1{*}team 2" will match "team 1 xyz team 2"
     */
    public AppPage verifyContains(String searchString) {
        AssertHelper.assertContainsRegex(searchString, getPageSource());
        return this;
    }
        
    /**
     * Verifies the status message in the page is same as the one specified.
     * @return The page (for chaining method calls).
     */
    public AppPage verifyStatus(String expectedStatus) {
        
        // The check is done multiple times with waiting times in between to account for
        // timing issues due to page load, inconsistencies in Selenium API, etc.
        for (int i = 0; i < VERIFICATION_RETRY_COUNT; i++) {
            if (i == VERIFICATION_RETRY_COUNT - 1) {
                // Last retry count: do one last attempt and if it still fails,
                // throw assertion error and show the difference
                waitForElementVisibility(statusMessage);
                assertEquals(expectedStatus, getStatus());
                break;
            }
            try {
                waitForElementVisibility(statusMessage);
                if (expectedStatus.equals(getStatus())) {
                    break;
                }
            } catch (NoSuchElementException | StaleElementReferenceException e) {
                // Might occur if the page reloads, which makes the previous WebElement
                // stored in the variable statusMessage "stale"
                ThreadHelper.waitFor(0);
            }
            ThreadHelper.waitFor(VERIFICATION_RETRY_DELAY_IN_MS);
        }

        return this;
    }

    /**
     * As of now, this simply verifies that the link is not broken. It does
     * not verify whether the file content is as expected. To be improved.
     */
    public void verifyDownloadLink(Url url) {
        //TODO: implement a better way to download a file and check content
        // (may be using HtmlUnit as the Webdriver?)
        String beforeReportDownloadUrl = browser.driver.getCurrentUrl();
        browser.driver.get(url.toAbsoluteString());
        String afterReportDownloadUrl = browser.driver.getCurrentUrl();
        assertEquals(beforeReportDownloadUrl, afterReportDownloadUrl);
    }
    
    /**
     * Verify if a file is downloadable based on the given url. If its downloadable,
     * download the file and get the SHA-1 hex of it and verify the hex with the given
     * expected hash.
     * 
     * Compute the expected hash of a file from http://onlinemd5.com/ (SHA-1)
     */
    public void verifyDownloadableFile(Url url, String expectedHash) throws Exception {
        
        URL fileToDownload = new URL(url.toAbsoluteString());
        
        String localDownloadPath = System.getProperty("java.io.tmpdir");
        File downloadedFile = new File(localDownloadPath + fileToDownload.getFile().replaceFirst("/|\\\\", ""));
        
        if (downloadedFile.exists()) {
            downloadedFile.delete();
        }
        if (!downloadedFile.canWrite()) {
            downloadedFile.setWritable(true);
        }
        
        SSLConnectionSocketFactory sslConnectionFactory =
                new SSLConnectionSocketFactory(SSLContexts.createDefault(), new AllowAllHostnameVerifier());
        
        CloseableHttpClient client = HttpClientBuilder.create().setSSLSocketFactory(sslConnectionFactory).build();
        
        HttpGet httpget = new HttpGet(fileToDownload.toURI());
        HttpParams httpRequestParameters = httpget.getParams();
        httpRequestParameters.setParameter(ClientPNames.HANDLE_REDIRECTS, false);
        httpget.setParams(httpRequestParameters);
 
        HttpResponse response = client.execute(httpget);
        FileUtils.copyInputStreamToFile(response.getEntity().getContent(), downloadedFile);
        response.getEntity().getContent().close();
 
        String downloadedFileAbsolutePath = downloadedFile.getAbsolutePath();
        assertTrue(new File(downloadedFileAbsolutePath).exists());
        
        String actualHash = DigestUtils.shaHex(new FileInputStream(downloadedFile));
        assertEquals(expectedHash.toLowerCase(), actualHash);
        
        client.close();
    }
    
    public void verifyFieldValue(String fieldId, String expectedValue) {
        assertEquals(expectedValue,
                browser.driver.findElement(By.id(fieldId)).getAttribute("value"));
    }
    
    /**
     * Verifies that the page source does not contain the given searchString.
     * 
     * @param searchString the substring that we want to omit from the page source
     * @return the AppPage
     */
    public AppPage verifyNotContain(String searchString) {
        String pageSource = getPageSource();
        assertFalse(pageSource.contains(searchString));
        return this;
    }
    
    private void respondToAlertWithRetry(WebElement elementToClick, boolean isConfirm) {
        elementToClick.click();
        waitForAlertPresence();
        Alert alert = browser.driver.switchTo().alert();
        if (isConfirm) {
            alert.accept();
        } else {
            alert.dismiss();
        }
    }
    
    private void respondToAlertWithRetryForHiddenElement(String hiddenElementIdToClick, boolean isConfirm) {
        JavascriptExecutor jsExecutor = (JavascriptExecutor) browser.driver;
        jsExecutor.executeScript("document.getElementById('" + hiddenElementIdToClick + "').click();");
        waitForAlertPresence();
        Alert alert = browser.driver.switchTo().alert();
        if (isConfirm) {
            alert.accept();
        } else {
            alert.dismiss();
        }
    }

    public void waitForAjaxLoaderGifToDisappear() {
        try {
            waitForElementToDisappear(By.xpath("//img[@src='/images/ajax-loader.gif' or @src='/images/ajax-preload.gif']"));
        } catch (NoSuchElementException alreadydisappears) {
            // ok to ignore
            return;
        }
    }

    public void changeToMobileView() {
        browser.driver.manage().window().setSize(new Dimension(360, 640));
    }

    public void changeToDesktopView() {
        browser.driver.manage().window().maximize();
    }

}
