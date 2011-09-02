<?php

function clean_input($str){
    $str = strip_tags(stripslashes(rtrim($str)));
    return $str;
}

$name = clean_input($_POST['name']);
$email = clean_input($_POST['email']);
$message = clean_input($_POST['message']);

$to = "test@test.localdomain";
$from = $email;
$today = date("d/m/Y H:i:s");
$subject = 'Email sent from '.$from.' to '.$to.': '.$today;
$headers = "MIME-Version: 1.0\r\n";
$headers .= "Content-type: text/html; charset=utf-8\r\n";
$message = "Message: ".$message;

mail($to, $subject, $message, $headers);

$return['msg'] = 'Email sent.';

die( json_encode($return));

?>
