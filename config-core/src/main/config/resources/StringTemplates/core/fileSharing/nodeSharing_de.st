<div style="font-family:Arial;font-size:11px">
<b>$senderUser.firstName$ $senderUser.lastName$</b> has shared folder <i>$ticket.resource.name$</i>.
You can see folder content <a href="$attachmentUrl$">here</a>.
$if(ticket.endDate)$Be careful this folder content is available until $ticket.endDate; format="MM/dd/yyyy"$.
$endif$

$if(additionalMessage)$
Message :
<div style="background-color:#FFF9D7; border:1px solid #E2C822; padding:5px; width:390px;"> $additionalMessage$ </div>
$endif$

</div>