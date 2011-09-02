

 $(document).ready(function() {

    $('#name').blur(function(){NameValidation();})
    $('#email').blur(function(){EmailValidation();})
    $('#message').blur(function(){MessageValidation();})
    $('#check').blur(function(){SumValidation();})

    $('#submit').click(function() 
    {
        if(! validate())
            return false;
        
        var conf = $('#confirmation');
        conf.text("Your message is being sent ...");

        var dataString = $("#chatform").serialize();

        $.ajax({
            url: "scripts/sendMail.php",
            type: "POST",
            data: dataString,
            dataType: "json",
            //timeout: 5000 ,
            success:function(data) 
            {
               if(data.error==true) {
                    conf.text("Your message could not be sent. Please try again later.");                  
                }
                else {                    
                    conf.text("Your message has been sent.");                   
                }
            },
            error:function(xhr,err,e) 
            {               
                conf.text("Your message could not be sent. Please try again later.");               
            }
        }); // closing $.ajax()

        return true;
    })      
});


function NameValidation() {

    var inputName = $('#name');
    
    inputName.val(trim(inputName.val()));
    var strValue = inputName.val();

    if(isEmpty(strValue)){
        inputName.addClass("input_error");
        return false;
    }
    
    var objRegExp = /^([a-z0-9_\'\-]+ *)*[a-z0-9]+$/i
    if(! objRegExp.test(strValue)){
        inputName.addClass("input_error");
        return false;
    }

    inputName.removeClass("input_error");

    return true;
}

function EmailValidation() {

    var emailInput = $('#email');

    emailInput.val(trim(emailInput.val()));
    var strValue = trim(emailInput.val());
    
    var emailExp = /^[\w\-\.\+]+\@[a-zA-Z0-9\.\-]+\.[a-zA-z0-9]{2,4}$/;
    if(isEmpty(strValue)) {
        emailInput.addClass("input_error");
        return false;
    }

    if(!strValue.match(emailExp)) {
        emailInput.addClass("input_error");
        return false;
    }

    emailInput.removeClass("input_error");

    return true;
}

function MessageValidation() {

    var messageInput = $('#message');
   
    messageInput.val(trim(messageInput.val()));
    var strValue = trim(messageInput.val());

    if(isEmpty(strValue)) {
        messageInput.addClass("input_error");
        return false;
    }

    messageInput.removeClass("input_error");
   
    return true;
}

function SumValidation() {

    var sumInput = $('#check');
  
    var strValue = trim(sumInput.val());
    if(isEmpty(strValue))    {
        sumInput.addClass("input_error");
        return false;
    }
    
    if(isNaN(strValue)) {
        sumInput.addClass("input_error");
        return false;
    }

    var n1 = parseInt(($('#n1')).text(), 10);
    var n2 = parseInt(($('#n2')).text(), 10);
    var s  = parseInt(strValue, 10);
    if((n1+n2)!= s) {
        sumInput.addClass("input_error");
        return false;
    }

    sumInput.removeClass("input_error");

    return true;
}

function validate() {

    var valid = true;

    if(!NameValidation())
        valid = false;
    if(!EmailValidation())
        valid = false;
    if(!MessageValidation())
        valid = false;
    if(!SumValidation())
        valid = false;

    return valid ;
}

function trim(stringToTrim) {
    return stringToTrim.replace(/^\s+|\s+$/g,"");
}

function isEmpty(strValue) {
    if(strValue.length == 0)
        return true;
    return false;
}

